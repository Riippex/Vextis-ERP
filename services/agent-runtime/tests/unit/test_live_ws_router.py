from collections.abc import AsyncIterator

from fastapi import FastAPI
from fastapi.testclient import TestClient
from google.genai import types

from vextis_agents.app.config import Settings
from vextis_agents.live.session_client import LiveSessionValidation
from vextis_agents.live.ws_router import create_live_router


class FakeBridge:
    def __init__(self, settings: Settings, tenant_id: str, conversation_id: str) -> None:
        self.tenant_id = tenant_id
        self.conversation_id = conversation_id
        self.sent_audio: list[bytes] = []
        self.ended = False
        self.closed = False

    def send_audio(self, data: bytes, mime_type: str = "audio/pcm;rate=16000") -> None:
        self.sent_audio.append(data)

    def end_utterance(self) -> None:
        self.ended = True

    async def events(self) -> AsyncIterator[types.Part]:
        yield types.Part(text="Hello from the agent")

    async def close(self) -> None:
        self.closed = True


class FakeSessionClient:
    def __init__(self, validation: LiveSessionValidation) -> None:
        self._validation = validation
        self.calls: list[tuple[str, str]] = []

    async def validate(
        self, session_id: str, token: str, correlation_id: str
    ) -> LiveSessionValidation:
        self.calls.append((session_id, token))
        return self._validation


def _build_app(session_client: FakeSessionClient, bridges: list[FakeBridge]) -> FastAPI:
    def bridge_factory(settings: Settings, tenant_id: str, conversation_id: str) -> FakeBridge:
        bridge = FakeBridge(settings, tenant_id, conversation_id)
        bridges.append(bridge)
        return bridge

    app = FastAPI()
    app.include_router(
        create_live_router(Settings(), session_client=session_client, bridge_factory=bridge_factory)  # type: ignore[arg-type]
    )
    return app


def test_valid_token_forwards_agent_events_and_client_audio() -> None:
    session_client = FakeSessionClient(
        LiveSessionValidation(valid=True, tenantId="demo-tenant", conversationId="conv-1")
    )
    bridges: list[FakeBridge] = []
    client = TestClient(_build_app(session_client, bridges))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        message = ws.receive_json()
        assert message == {"type": "transcript", "text": "Hello from the agent"}

        ws.send_bytes(b"\x01\x02")
        ws.send_json({"type": "end_utterance"})

    assert session_client.calls == [("session-1", "opaque-token")]
    assert len(bridges) == 1
    assert bridges[0].sent_audio == [b"\x01\x02"]
    assert bridges[0].ended is True
    assert bridges[0].closed is True


def test_missing_auth_type_closes_the_connection() -> None:
    session_client = FakeSessionClient(LiveSessionValidation(valid=True))
    bridges: list[FakeBridge] = []
    client = TestClient(_build_app(session_client, bridges))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"token": "opaque-token"})
        try:
            ws.receive_text()
            raised = False
        except Exception:
            raised = True

    assert raised is True
    assert session_client.calls == []
    assert bridges == []


def test_invalid_session_token_closes_the_connection_without_a_bridge() -> None:
    session_client = FakeSessionClient(LiveSessionValidation(valid=False))
    bridges: list[FakeBridge] = []
    client = TestClient(_build_app(session_client, bridges))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "wrong-token"})
        try:
            ws.receive_text()
            raised = False
        except Exception:
            raised = True

    assert raised is True
    assert session_client.calls == [("session-1", "wrong-token")]
    assert bridges == []
