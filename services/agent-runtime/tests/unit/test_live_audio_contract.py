from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta

from fastapi import FastAPI
from fastapi.testclient import TestClient
from google.genai import types

from vextis_agents.app.config import Settings
from vextis_agents.live.session_client import (
    LiveSessionValidation,
    LiveSessionValidationError,
)
from vextis_agents.live.ws_router import create_live_router


class MockAudioBridge:
    def __init__(self, settings: Settings, tenant_id: str, conversation_id: str) -> None:
        self.tenant_id = tenant_id
        self.conversation_id = conversation_id
        self.received_audio: list[bytes] = []
        self.utterance_ended = False
        self.closed = False

    def send_audio(self, data: bytes, mime_type: str = "audio/pcm;rate=16000") -> None:
        self.received_audio.append(data)

    def end_utterance(self) -> None:
        self.utterance_ended = True

    async def events(self) -> AsyncIterator[types.Part]:
        # Return mock assistant audio response bytes and transcript
        yield types.Part(
            inline_data=types.Blob(
                mime_type="audio/pcm;rate=24000",
                data=b"\x00\x01\x02\x03\x04\x05",
            )
        )
        yield types.Part(text="Transcribed assistant reply")

    async def close(self) -> None:
        self.closed = True


class MockSessionValidator:
    def __init__(self, should_fail: bool = False, is_valid: bool = True) -> None:
        self.should_fail = should_fail
        self.is_valid = is_valid
        self.validation_calls: list[tuple[str, str, str]] = []

    async def validate(
        self, session_id: str, token: str, correlation_id: str
    ) -> LiveSessionValidation:
        self.validation_calls.append((session_id, token, correlation_id))
        if self.should_fail:
            raise LiveSessionValidationError("Core validation service unreachable")
        # Enterprise Core always returns the session's expiry alongside the
        # verdict; Agent Runtime now refuses to open a bridge without one.
        expires_at = (datetime.now(UTC) + timedelta(minutes=5)).isoformat().replace("+00:00", "Z")
        return LiveSessionValidation(
            valid=self.is_valid,
            tenantId="demo-tenant" if self.is_valid else None,
            conversationId="conv-live-1" if self.is_valid else None,
            expiresAt=expires_at if self.is_valid else None,
        )


def _build_test_app(validator: MockSessionValidator, bridges: list[MockAudioBridge]) -> FastAPI:
    def make_bridge(
        settings: Settings, tenant_id: str, conversation_id: str
    ) -> MockAudioBridge:
        bridge = MockAudioBridge(settings, tenant_id, conversation_id)
        bridges.append(bridge)
        return bridge

    app = FastAPI()
    app.include_router(
        create_live_router(
            Settings(),
            session_client=validator,  # type: ignore[arg-type]
            bridge_factory=make_bridge,  # type: ignore[arg-type]
        )
    )
    return app


def test_live_session_audio_streaming_and_event_reception() -> None:
    validator = MockSessionValidator(is_valid=True)
    bridges: list[MockAudioBridge] = []
    client = TestClient(_build_test_app(validator, bridges))

    with client.websocket_connect("/v1/live/live-sess-1") as ws:
        ws.send_json({"type": "auth", "token": "valid-token-xyz"})

        # First incoming message should be binary audio response
        audio_bytes = ws.receive_bytes()
        assert audio_bytes == b"\x00\x01\x02\x03\x04\x05"

        # Second incoming message should be transcript
        transcript_msg = ws.receive_json()
        assert transcript_msg == {
            "type": "transcript",
            "text": "Transcribed assistant reply",
        }

        # Client streams 16kHz PCM audio chunk
        pcm_chunk = b"\x10\x20\x30\x40"
        ws.send_bytes(pcm_chunk)
        ws.send_json({"type": "end_utterance"})
        ws.send_json({"type": "close"})

    assert len(validator.validation_calls) == 1
    assert validator.validation_calls[0][0] == "live-sess-1"
    assert validator.validation_calls[0][1] == "valid-token-xyz"
    assert len(bridges) == 1
    assert bridges[0].received_audio == [pcm_chunk]
    assert bridges[0].utterance_ended is True
    assert bridges[0].closed is True


def test_live_session_closes_on_core_validation_outage() -> None:
    validator = MockSessionValidator(should_fail=True)
    bridges: list[MockAudioBridge] = []
    client = TestClient(_build_test_app(validator, bridges))

    with client.websocket_connect("/v1/live/live-sess-outage") as ws:
        ws.send_json({"type": "auth", "token": "any-token"})
        try:
            ws.receive_bytes()
            raised = False
        except Exception:
            raised = True

    assert raised is True
    assert len(validator.validation_calls) == 1
    assert len(bridges) == 0
