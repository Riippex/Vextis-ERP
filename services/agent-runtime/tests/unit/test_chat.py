from collections.abc import AsyncIterator

import pytest
from fastapi.testclient import TestClient
from google.genai import types
from pydantic import SecretStr

from vextis_agents.app import chat as chat_module
from vextis_agents.app.config import Settings
from vextis_agents.app.main import create_app


class FakeEvent:
    def __init__(self, output: str) -> None:
        self.content = types.Content(parts=[types.Part(text=output)])

    def is_final_response(self) -> bool:
        return True


class FakeRunner:
    def __init__(self, output: str) -> None:
        self.output = output
        self.message: types.Content | None = None
        self.closed = False

    async def run_async(self, **kwargs: object) -> AsyncIterator[FakeEvent]:
        message = kwargs["new_message"]
        assert isinstance(message, types.Content)
        self.message = message
        yield FakeEvent(self.output)

    async def close(self) -> None:
        self.closed = True


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
            "conversationId": "9c6a6a2e-2f39-4b6a-9a8a-3b0e6a2c1d10",
            "message": "What is the status of PO-2026-001?",
        },
        headers={"Authorization": "Bearer s3cret-core-callback-token"},
    )

    assert response.status_code == 200
    assert response.json() == {"reply": "Order PO-2026-001 is currently in planning."}
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


def test_chat_route_is_absent_when_chat_is_disabled() -> None:
    app = create_app(Settings(chat_enabled=False))

    response = TestClient(app).post(
        "/v1/chat/complete",
        json={"tenantId": "demo-tenant", "conversationId": "x", "message": "Hello"},
    )

    assert response.status_code == 404
