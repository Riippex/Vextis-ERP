from collections.abc import AsyncIterator

from google.adk.agents import LiveRequestQueue
from google.adk.runners import InMemoryRunner
from google.genai import types

from vextis_agents.adk_runner import enable_session_auto_creation
from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator


class LiveVoiceBridge:
    """
    Bridges one WebSocket connection's audio frames to and from the ADK
    coordinator's bidirectional Live session. Uses the same agent/tool
    configuration as the rest of the coordinator (build_coordinator), so any
    business mutation it decides to make still goes through the existing
    authenticated /internal/agent-tools/** path — this is a transport
    difference, not a second set of use cases.
    """

    def __init__(self, settings: Settings, tenant_id: str, conversation_id: str) -> None:
        self._runner = enable_session_auto_creation(
            InMemoryRunner(
                agent=build_coordinator(
                    settings,
                    tenant_id,
                    model=settings.live_model,
                    correlation_id=conversation_id,
                ),
                app_name="vextis_ask_vextis_live",
            )
        )
        self._queue = LiveRequestQueue()
        self._tenant_id = tenant_id
        self._conversation_id = conversation_id

    def send_audio(self, data: bytes, mime_type: str = "audio/pcm;rate=16000") -> None:
        self._queue.send_realtime(types.Blob(data=data, mime_type=mime_type))

    def end_utterance(self) -> None:
        self._queue.send_audio_stream_end()

    async def events(self) -> AsyncIterator[types.Part]:
        """Yields response parts (audio and/or transcript text) as the model produces them."""
        async for event in self._runner.run_live(
            user_id=self._tenant_id,
            session_id=f"live-{self._conversation_id}",
            live_request_queue=self._queue,
        ):
            if event.content is None:
                continue
            for part in event.content.parts or []:
                yield part

    async def close(self) -> None:
        self._queue.close()
        await self._runner.close()
