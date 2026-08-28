import asyncio
from collections.abc import AsyncIterator, Callable
from datetime import UTC, datetime, timedelta

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from google.genai import types
from starlette.websockets import WebSocketDisconnect

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


class NeverEndingBridge(FakeBridge):
    """Streams nothing and never finishes, so cancellation has to do the work."""

    def __init__(self, settings: Settings, tenant_id: str, conversation_id: str) -> None:
        super().__init__(settings, tenant_id, conversation_id)
        self.events_cancelled = False

    async def events(self) -> AsyncIterator[types.Part]:
        try:
            await asyncio.Event().wait()
        except asyncio.CancelledError:
            self.events_cancelled = True
            raise
        yield types.Part(text="unreachable")


class FakeSessionClient:
    def __init__(self, validation: LiveSessionValidation) -> None:
        self._validation = validation
        self.calls: list[tuple[str, str]] = []

    async def validate(
        self, session_id: str, token: str, correlation_id: str
    ) -> LiveSessionValidation:
        self.calls.append((session_id, token))
        return self._validation


def _iso(moment: datetime) -> str:
    return moment.astimezone(UTC).isoformat().replace("+00:00", "Z")


def _valid(expires_at: str | None) -> LiveSessionValidation:
    return LiveSessionValidation(
        valid=True,
        tenantId="demo-tenant",
        conversationId="conv-1",
        expiresAt=expires_at,
    )


def _build_app(
    session_client: FakeSessionClient,
    bridges: list[FakeBridge],
    settings: Settings | None = None,
    bridge_class: type[FakeBridge] = FakeBridge,
    clock: Callable[[], datetime] | None = None,
) -> FastAPI:
    def bridge_factory(
        bridge_settings: Settings, tenant_id: str, conversation_id: str
    ) -> FakeBridge:
        bridge = bridge_class(bridge_settings, tenant_id, conversation_id)
        bridges.append(bridge)
        return bridge

    app = FastAPI()
    app.include_router(
        create_live_router(
            settings or Settings(),
            session_client=session_client,  # type: ignore[arg-type]
            bridge_factory=bridge_factory,  # type: ignore[arg-type]
            clock=clock,
        )
    )
    return app


def _far_future() -> str:
    return _iso(datetime.now(UTC) + timedelta(minutes=5))


def test_valid_token_forwards_agent_events_and_client_audio() -> None:
    session_client = FakeSessionClient(_valid(_far_future()))
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


def test_session_already_expired_at_validation_is_refused() -> None:
    reference = datetime(2026, 8, 27, 22, 0, 0, tzinfo=UTC)
    session_client = FakeSessionClient(_valid(_iso(reference - timedelta(seconds=1))))
    bridges: list[FakeBridge] = []
    client = TestClient(_build_app(session_client, bridges, clock=lambda: reference))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        with pytest.raises(WebSocketDisconnect) as disconnect:
            ws.receive_text()

    assert disconnect.value.code == 4401
    assert bridges == []


def test_validation_without_an_expiry_is_refused() -> None:
    session_client = FakeSessionClient(_valid(None))
    bridges: list[FakeBridge] = []
    client = TestClient(_build_app(session_client, bridges))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        with pytest.raises(WebSocketDisconnect) as disconnect:
            ws.receive_text()

    assert disconnect.value.code == 4401
    assert bridges == []


def test_live_connection_closes_when_core_expiry_passes_mid_session() -> None:
    # Short real TTL: the socket stays healthy and idle, so only the expiry can
    # end it. The bridge never finishes on its own, proving the teardown runs.
    expires_at = _iso(datetime.now(UTC) + timedelta(milliseconds=250))
    session_client = FakeSessionClient(_valid(expires_at))
    bridges: list[FakeBridge] = []
    client = TestClient(
        _build_app(session_client, bridges, bridge_class=NeverEndingBridge)
    )

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        with pytest.raises(WebSocketDisconnect) as disconnect:
            ws.receive_text()

    assert disconnect.value.code == 4401
    assert len(bridges) == 1
    bridge = bridges[0]
    assert isinstance(bridge, NeverEndingBridge)
    assert bridge.closed is True
    assert bridge.events_cancelled is True


def test_deployment_ceiling_caps_a_long_core_expiry() -> None:
    # Enterprise Core hands out a much longer session than this deployment
    # allows; the ceiling has to win.
    session_client = FakeSessionClient(_valid(_iso(datetime.now(UTC) + timedelta(hours=2))))
    bridges: list[FakeBridge] = []
    settings = Settings(live_max_session_seconds=1)
    client = TestClient(
        _build_app(session_client, bridges, settings=settings, bridge_class=NeverEndingBridge)
    )

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        with pytest.raises(WebSocketDisconnect) as disconnect:
            ws.receive_text()

    assert disconnect.value.code == 4401
    assert bridges[0].closed is True


def test_unauthenticated_connection_is_dropped_after_the_auth_timeout() -> None:
    session_client = FakeSessionClient(_valid(_far_future()))
    bridges: list[FakeBridge] = []
    settings = Settings(live_auth_timeout_seconds=0.2)
    client = TestClient(_build_app(session_client, bridges, settings=settings))

    with (
        client.websocket_connect("/v1/live/session-1") as ws,
        pytest.raises(WebSocketDisconnect) as disconnect,
    ):
        ws.receive_text()

    assert disconnect.value.code == 4401
    assert session_client.calls == []
    assert bridges == []


def test_oversized_auth_frame_is_rejected_before_validation() -> None:
    session_client = FakeSessionClient(_valid(_far_future()))
    bridges: list[FakeBridge] = []
    settings = Settings(live_max_text_frame_bytes=32)
    client = TestClient(_build_app(session_client, bridges, settings=settings))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "x" * 4096})
        with pytest.raises(WebSocketDisconnect) as disconnect:
            ws.receive_text()

    assert disconnect.value.code == 1009
    assert session_client.calls == []
    assert bridges == []


def test_oversized_audio_frame_closes_the_session() -> None:
    session_client = FakeSessionClient(_valid(_far_future()))
    bridges: list[FakeBridge] = []
    settings = Settings(live_max_audio_frame_bytes=16)
    client = TestClient(
        _build_app(session_client, bridges, settings=settings, bridge_class=NeverEndingBridge)
    )

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        ws.send_bytes(b"\x00" * 64)
        with pytest.raises(WebSocketDisconnect) as disconnect:
            ws.receive_text()

    assert disconnect.value.code == 1009
    assert bridges[0].sent_audio == []
    assert bridges[0].closed is True


def test_malformed_text_frame_does_not_break_the_session() -> None:
    session_client = FakeSessionClient(_valid(_far_future()))
    bridges: list[FakeBridge] = []
    client = TestClient(_build_app(session_client, bridges))

    with client.websocket_connect("/v1/live/session-1") as ws:
        ws.send_json({"type": "auth", "token": "opaque-token"})
        assert ws.receive_json() == {"type": "transcript", "text": "Hello from the agent"}
        ws.send_text("not json at all")
        ws.send_bytes(b"\x09")
        ws.send_json({"type": "close"})

    assert bridges[0].sent_audio == [b"\x09"]
    assert bridges[0].closed is True
