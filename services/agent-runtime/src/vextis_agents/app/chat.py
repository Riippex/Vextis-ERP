import hmac
import logging
from uuid import uuid4

from fastapi import APIRouter, Header, HTTPException, status
from google.adk.runners import InMemoryRunner
from google.genai import types
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator

logger = logging.getLogger(__name__)


class ChatCompleteRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    tenant_id: str = Field(alias="tenantId")
    conversation_id: str = Field(alias="conversationId")
    message: str


class ChatCompleteResponse(BaseModel):
    reply: str


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
                settings, request.tenant_id, correlation_id=request.conversation_id
            ),
            app_name="vextis_ask_vextis",
        )
        message = types.Content(role="user", parts=[types.Part(text=request.message)])
        final_text: str | None = None
        try:
            async for event in runner.run_async(
                user_id=request.tenant_id,
                session_id=f"chat-{request.conversation_id}-{uuid4()}",
                new_message=message,
            ):
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
        return ChatCompleteResponse(reply=final_text)

    return router
