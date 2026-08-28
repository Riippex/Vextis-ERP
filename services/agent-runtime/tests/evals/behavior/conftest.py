"""
Shared harness for the behavioural evaluations.

Unlike the deterministic suites, these run the real ADK coordinator against a
real Vertex AI model and assert on what it does. Enterprise Core is stubbed with
an httpx MockTransport so a scenario is reproducible and costs nothing beyond
model tokens; the model, the prompts, the tool wiring and the delegation are all
the production ones.

Run them with:

    uv run pytest -m model_eval

They are deselected by default because they need Vertex credentials and are
non-deterministic, which does not belong in the CI gate.
"""

import os
from collections.abc import Callable, Iterator
from dataclasses import dataclass, field
from uuid import uuid4

import httpx
import pytest
from google.adk.agents import LlmAgent
from google.adk.runners import InMemoryRunner
from google.genai import types
from pydantic import SecretStr

from vextis_agents.adk_runner import enable_session_auto_creation
from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator
from vextis_agents.rag.embedding import DeterministicMockEmbedder
from vextis_agents.rag.retriever import KnowledgeRetriever
from vextis_agents.tools.core_api.business_reads import EnterpriseCoreBusinessReadClient

TENANT = "demo-tenant"


@dataclass
class AgentTurn:
    """What the fleet produced for one prompt."""

    reply: str
    authors: list[str] = field(default_factory=list)
    tool_calls: list[str] = field(default_factory=list)

    @property
    def reply_lower(self) -> str:
        return self.reply.lower()

    def mentions_any(self, *needles: str) -> bool:
        return any(needle.lower() in self.reply_lower for needle in needles)


def _require_vertex() -> Settings:
    project = os.environ.get("GOOGLE_CLOUD_PROJECT")
    model = os.environ.get("VEXTIS_GEMINI_MODEL")
    if not project or not model:
        pytest.skip(
            "Behavioural evals need GOOGLE_CLOUD_PROJECT and VEXTIS_GEMINI_MODEL plus "
            "Vertex AI credentials"
        )
    return Settings(
        google_cloud_project=project,
        gemini_model=model,
        enterprise_core_url="https://core.vextis.invalid",
        agent_tools_token=SecretStr("eval-token"),
        rag_mock_embeddings_enabled=True,
    )


@pytest.fixture
def settings() -> Settings:
    return _require_vertex()


CoreHandler = Callable[[httpx.Request], httpx.Response]


def build_fleet(
    settings: Settings,
    core_handler: CoreHandler,
    knowledge_handler: CoreHandler | None = None,
) -> LlmAgent:
    """
    The production coordinator with its real prompts and sub-agents, wired to a
    stubbed Enterprise Core.
    """
    correlation_id = str(uuid4())
    core_reads = EnterpriseCoreBusinessReadClient(
        settings,
        tenant_id=TENANT,
        correlation_id=correlation_id,
        transport=httpx.MockTransport(core_handler),
    )
    retriever = None
    if knowledge_handler is not None:
        retriever = KnowledgeRetriever(
            settings=settings,
            tenant_id=TENANT,
            correlation_id=correlation_id,
            embedder=DeterministicMockEmbedder(),
            transport=httpx.MockTransport(knowledge_handler),
        )
    return build_coordinator(
        settings,
        TENANT,
        correlation_id=correlation_id,
        core_reads=core_reads,
        knowledge_retriever=retriever,
    )


async def ask(agent: LlmAgent, prompt: str) -> AgentTurn:
    """Runs one user turn through the fleet and reports what came back."""
    runner = enable_session_auto_creation(
        InMemoryRunner(agent=agent, app_name="vextis_behaviour_eval")
    )
    message = types.Content(role="user", parts=[types.Part(text=prompt)])
    turn = AgentTurn(reply="")
    collected: list[str] = []
    try:
        async for event in runner.run_async(
            user_id=TENANT,
            session_id=f"eval-{uuid4()}",
            new_message=message,
        ):
            if event.author and event.author not in turn.authors:
                turn.authors.append(event.author)
            for part in (event.content.parts or []) if event.content else []:
                if part.function_call is not None and part.function_call.name:
                    turn.tool_calls.append(part.function_call.name)
            if event.is_final_response() and event.content is not None:
                collected.extend(part.text for part in (event.content.parts or []) if part.text)
    finally:
        await runner.close()

    turn.reply = "".join(collected)
    return turn


@pytest.fixture
def refusing_core() -> Iterator[CoreHandler]:
    """Enterprise Core denying every tool call, as it does for a disallowed agent."""

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"error": "Agent is not authorized for tool or tenant"})

    yield handler
