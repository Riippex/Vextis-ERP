import hmac
import logging
import re
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Header, HTTPException, status
from google.adk.runners import InMemoryRunner
from google.genai import types
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator

logger = logging.getLogger(__name__)


class ChatCompleteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    tenant_id: str = Field(alias="tenantId", min_length=1, max_length=100)
    conversation_id: UUID = Field(alias="conversationId")
    message: str = Field(min_length=1, max_length=4000)


class AgentActivity(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    agent_id: str = Field(
        alias="agentId", min_length=1, max_length=150, pattern=r"^[A-Za-z0-9._-]+$"
    )
    tools: list[str] = Field(default_factory=list, max_length=8)


class ChatCompleteResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reply: str = Field(min_length=1, max_length=12000)
    activities: list[AgentActivity] = Field(default_factory=list, max_length=4)


class PublicActivityCollector:
    """Collects bounded public ADK metadata without arguments, results, or reasoning."""

    def __init__(self, allowed_agents: set[str]) -> None:
        self._allowed_agents = frozenset(allowed_agents)
        self._tools_by_agent: dict[str, list[str]] = {}

    def observe(self, event: Any) -> None:
        author = getattr(event, "author", None)
        if not isinstance(author, str) or author not in self._allowed_agents:
            return
        tools = self._tools_by_agent.setdefault(author, [])
        content = getattr(event, "content", None)
        for part in getattr(content, "parts", None) or []:
            function_call = getattr(part, "function_call", None)
            name = getattr(function_call, "name", None)
            if (
                isinstance(name, str)
                and re.fullmatch(r"[a-z0-9_]{1,100}", name)
                and name not in tools
                and len(tools) < 8
            ):
                tools.append(name)

    def activities(self) -> list[AgentActivity]:
        return [
            AgentActivity(agentId=agent_id, tools=tools)
            for agent_id, tools in list(self._tools_by_agent.items())[:4]
        ]


def create_chat_router(settings: Settings) -> APIRouter:
    """
    Bridges Enterprise Core's `askVextis` mutation to the same coordinator
    agent used elsewhere, as a single-shot text turn (no Live/voice session).
    Any business mutation the agent decides to make still goes through the
    existing authenticated /internal/agent-tools/** tool-calling path.
    """
    router = APIRouter()
    expected_token = (
        settings.core_callback_token.get_secret_value() if settings.core_callback_token else ""
    )

    @router.post("/v1/chat/complete", response_model=ChatCompleteResponse)
    async def complete_chat(
        request: ChatCompleteRequest,
        authorization: str = Header(default=""),
    ) -> ChatCompleteResponse:
        if not expected_token or not hmac.compare_digest(authorization, f"Bearer {expected_token}"):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid service credential"
            )

        runner = InMemoryRunner(
            agent=build_coordinator(
                settings, request.tenant_id, correlation_id=str(request.conversation_id)
            ),
            app_name="vextis_ask_vextis",
        )
        activity = PublicActivityCollector(
            {
                settings.coordinator_logical_agent_id,
                settings.crm_agent_id,
                settings.inventory_agent_id,
                settings.billing_agent_id,
            }
        )
        message = types.Content(role="user", parts=[types.Part(text=request.message)])
        final_text: str | None = None
        try:
            async for event in runner.run_async(
                user_id=request.tenant_id,
                session_id=f"chat-{request.conversation_id}-{uuid4()}",
                new_message=message,
            ):
                activity.observe(event)
                if event.is_final_response() and event.content is not None:
                    parts = event.content.parts or []
                    texts = [part.text for part in parts if part.text]
                    if texts:
                        final_text = "".join(texts)
        finally:
            await runner.close()

        if final_text is None:
            logger.warning(
                "Ask Vextis got no final response for conversation %s", request.conversation_id
            )
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Gemini returned no reply"
            )
        if len(final_text) > 12000:
            logger.warning(
                "Ask Vextis reply exceeded the public response bound for conversation %s",
                request.conversation_id,
            )
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Gemini reply exceeded the supported size",
            )
        return ChatCompleteResponse(reply=final_text, activities=activity.activities())

    return router
