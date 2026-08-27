from collections.abc import AsyncIterator

import pytest
from fastapi.testclient import TestClient
from google.genai import types
from pydantic import SecretStr

from vextis_agents.app import chat as chat_module
from vextis_agents.app.config import Settings
from vextis_agents.app.main import create_app
from vextis_agents.memory import MemoryTurn


class FakeEvent:
    def __init__(
        self,
        *,
        author: str,
        output: str | None = None,
        tool: str | None = None,
    ) -> None:
        self.author = author
        parts: list[types.Part] = []
        if output is not None:
            parts.append(types.Part(text=output))
        if tool is not None:
            parts.append(
                types.Part(
                    function_call=types.FunctionCall(name=tool, args={"secret": "not exposed"})
                )
            )
        self.content = types.Content(parts=parts)
        self._final = output is not None

    def is_final_response(self) -> bool:
        return self._final


class FakeRunner:
    def __init__(self, output: str) -> None:
        self.output = output
        self.message: types.Content | None = None
        self.closed = False

    async def run_async(self, **kwargs: object) -> AsyncIterator[FakeEvent]:
        message = kwargs["new_message"]
        assert isinstance(message, types.Content)
        self.message = message
        yield FakeEvent(author="vextis_inventory_agent", tool="get_stock")
        yield FakeEvent(author="vextis_coordinator", output=self.output)

    async def close(self) -> None:
        self.closed = True


class FakeAgentMemory:
    @property
    def provider(self) -> str:
        return "VERTEX_AI_MEMORY_BANK"

    async def prepare_turn(self, tenant_id: str, actor_id: str, message: str) -> MemoryTurn:
        assert tenant_id == "demo-tenant"
        assert actor_id == "firebase-user-123"
        return MemoryTurn(self.provider, True, ("Language preference: Spanish.",), False)


def _settings() -> Settings:
    return Settings(
        chat_enabled=True,
        core_callback_token=SecretStr("s3cret-core-callback-token"),
        agent_tools_token=SecretStr("agent-tools-token"),
        gemini_model="gemini-test",
        google_cloud_project="vextis-test",
    )


def test_complete_chat_returns_the_agent_reply_when_authorized(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runner = FakeRunner("Order PO-2026-001 is currently in planning.")
    monkeypatch.setattr(chat_module, "InMemoryRunner", lambda **_: runner)
    app = create_app(_settings())

    response = TestClient(app).post(
        "/v1/chat/complete",
        json={
            "tenantId": "demo-tenant",
            "actorId": "firebase-user-123",
            "conversationId": "9c6a6a2e-2f39-4b6a-9a8a-3b0e6a2c1d10",
            "message": "What is the status of PO-2026-001?",
        },
        headers={"Authorization": "Bearer s3cret-core-callback-token"},
    )

    assert response.status_code == 200
    assert response.json() == {
        "reply": "Order PO-2026-001 is currently in planning.",
        "activities": [
            {"agentId": "vextis_inventory_agent", "tools": ["get_stock"]},
            {"agentId": "vextis_coordinator", "tools": []},
        ],
        "memory": None,
    }
    assert runner.message is not None
    assert runner.closed is True


def test_complete_chat_rejects_a_missing_or_wrong_credential(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(chat_module, "InMemoryRunner", lambda **_: FakeRunner("unused"))
    app = create_app(_settings())
    client = TestClient(app)
    body = {
        "tenantId": "demo-tenant",
        "actorId": "firebase-user-123",
        "conversationId": "9c6a6a2e-2f39-4b6a-9a8a-3b0e6a2c1d10",
        "message": "Hello",
    }

    assert client.post("/v1/chat/complete", json=body).status_code == 401
    assert (
        client.post(
            "/v1/chat/complete", json=body, headers={"Authorization": "Bearer wrong-token"}
        ).status_code
        == 401
    )


def test_complete_chat_injects_bounded_preferences_and_returns_only_metadata(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runner = FakeRunner("El pedido está en planificación.")
    monkeypatch.setattr(chat_module, "InMemoryRunner", lambda **_: runner)
    app = create_app(_settings(), agent_memory=FakeAgentMemory())

    response = TestClient(app).post(
        "/v1/chat/complete",
        json={
            "tenantId": "demo-tenant",
            "actorId": "firebase-user-123",
            "conversationId": "9c6a6a2e-2f39-4b6a-9a8a-3b0e6a2c1d10",
            "message": "Show my order",
        },
        headers={"Authorization": "Bearer s3cret-core-callback-token"},
    )

    assert response.status_code == 200
    assert response.json()["memory"] == {
        "provider": "VERTEX_AI_MEMORY_BANK",
        "available": True,
        "contextCount": 1,
        "preferenceStored": False,
    }
    assert "Language preference: Spanish." in str(runner.message)
    assert "Language preference: Spanish." not in str(response.json())


def test_explicit_memory_command_fails_when_memory_is_disabled() -> None:
    app = create_app(_settings())

    response = TestClient(app).post(
        "/v1/chat/complete",
        json={
            "tenantId": "demo-tenant",
            "actorId": "firebase-user-123",
            "conversationId": "9c6a6a2e-2f39-4b6a-9a8a-3b0e6a2c1d10",
            "message": "Remember preference: concise",
        },
        headers={"Authorization": "Bearer s3cret-core-callback-token"},
    )

    assert response.status_code == 503


def test_chat_route_is_absent_when_chat_is_disabled() -> None:
    app = create_app(Settings(chat_enabled=False))

    response = TestClient(app).post(
        "/v1/chat/complete",
        json={
            "tenantId": "demo-tenant",
            "actorId": "firebase-user-123",
            "conversationId": "x",
            "message": "Hello",
        },
    )

    assert response.status_code == 404
