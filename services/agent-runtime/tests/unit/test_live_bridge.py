from collections.abc import AsyncIterator

import pytest
from google.adk.agents import LiveRequestQueue
from google.genai import types
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.live import bridge as bridge_module
from vextis_agents.live.bridge import LiveVoiceBridge


class FakeEvent:
    def __init__(self, *, text: str | None = None, audio: bytes | None = None) -> None:
        parts: list[types.Part] = []
        if text is not None:
            parts.append(types.Part(text=text))
        if audio is not None:
            blob = types.Blob(data=audio, mime_type="audio/pcm;rate=24000")
            parts.append(types.Part(inline_data=blob))
        self.content = types.Content(parts=parts) if parts else None


class FakeRunner:
    def __init__(self, events: list[FakeEvent]) -> None:
        self._events = events
        self.live_request_queue: LiveRequestQueue | None = None
        self.closed = False

    async def run_live(self, **kwargs: object) -> AsyncIterator[FakeEvent]:
        queue = kwargs["live_request_queue"]
        assert isinstance(queue, LiveRequestQueue)
        self.live_request_queue = queue
        for event in self._events:
            yield event

    async def close(self) -> None:
        self.closed = True


def settings() -> Settings:
    return Settings(live_model="gemini-live-test", agent_tools_token=SecretStr("agent-tools-token"))


@pytest.mark.asyncio
async def test_events_yields_both_audio_and_transcript_parts(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runner = FakeRunner([FakeEvent(audio=b"\x00\x01"), FakeEvent(text="Hello there")])
    monkeypatch.setattr(bridge_module, "InMemoryRunner", lambda **_: runner)

    bridge = LiveVoiceBridge(settings(), tenant_id="demo-tenant", conversation_id="conv-1")
    parts = [part async for part in bridge.events()]

    assert parts[0].inline_data is not None
    assert parts[0].inline_data.data == b"\x00\x01"
    assert parts[1].text == "Hello there"


@pytest.mark.asyncio
async def test_send_audio_and_end_utterance_enqueue_onto_the_live_request_queue(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runner = FakeRunner([])
    monkeypatch.setattr(bridge_module, "InMemoryRunner", lambda **_: runner)

    bridge = LiveVoiceBridge(settings(), tenant_id="demo-tenant", conversation_id="conv-1")
    bridge.send_audio(b"\x01\x02", mime_type="audio/pcm;rate=16000")
    bridge.end_utterance()

    # Draining events() is what actually calls run_live and captures the queue.
    async for _ in bridge.events():
        pass

    assert runner.live_request_queue is not None
    first = await runner.live_request_queue.get()
    assert first.blob is not None
    assert first.blob.data == b"\x01\x02"
    second = await runner.live_request_queue.get()
    assert second.audio_stream_end is True


@pytest.mark.asyncio
async def test_close_closes_both_the_queue_and_the_runner(monkeypatch: pytest.MonkeyPatch) -> None:
    runner = FakeRunner([])
    monkeypatch.setattr(bridge_module, "InMemoryRunner", lambda **_: runner)

    bridge = LiveVoiceBridge(settings(), tenant_id="demo-tenant", conversation_id="conv-1")
    await bridge.close()

    assert runner.closed is True
