import asyncio
import json
import logging
from collections.abc import Callable
from uuid import uuid4

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, ValidationError
from starlette.websockets import WebSocketState

from vextis_agents.app.config import Settings
from vextis_agents.live.bridge import LiveVoiceBridge
from vextis_agents.live.session_client import (
    EnterpriseCoreLiveSessionClient,
    LiveSessionValidationError,
)

logger = logging.getLogger(__name__)

AUTH_TIMEOUT_SECONDS = 5.0


class _AuthMessage(BaseModel):
    type: str
    token: str


def create_live_router(
    settings: Settings,
    session_client: EnterpriseCoreLiveSessionClient | None = None,
    bridge_factory: Callable[[Settings, str, str], LiveVoiceBridge] | None = None,
) -> APIRouter:
    """
    The browser opens this WebSocket after Enterprise Core authorizes a Live
    session (createLiveSession). Its first frame must be the session token —
    never a query-string token, which would otherwise leak into access logs.
    """
    router = APIRouter()
    client = session_client or EnterpriseCoreLiveSessionClient(settings)
    make_bridge = bridge_factory or LiveVoiceBridge

    @router.websocket("/v1/live/{session_id}")
    async def live_session(websocket: WebSocket, session_id: str) -> None:
        await websocket.accept()
        correlation_id = str(uuid4())

        auth_message = await _receive_auth_message(websocket)
        if auth_message is None:
            return
        if auth_message.type != "auth":
            await websocket.close(code=4401, reason="First message must be {type: auth, token}")
            return

        try:
            validation = await client.validate(session_id, auth_message.token, correlation_id)
        except LiveSessionValidationError:
            logger.warning("Could not reach Enterprise Core to validate session %s", session_id)
            await websocket.close(code=1011, reason="Could not validate the session")
            return

        if not validation.valid or validation.conversation_id is None:
            await websocket.close(code=4401, reason="Invalid or expired session token")
            return

        bridge = make_bridge(settings, validation.tenant_id or "", validation.conversation_id)
        forward_task = asyncio.create_task(_forward_agent_events(websocket, bridge))
        try:
            await _receive_audio(websocket, bridge)
        except WebSocketDisconnect:
            pass
        finally:
            forward_task.cancel()
            await bridge.close()
            if websocket.client_state == WebSocketState.CONNECTED:
                await websocket.close()

    return router


async def _receive_auth_message(websocket: WebSocket) -> _AuthMessage | None:
    try:
        raw = await asyncio.wait_for(websocket.receive_json(), timeout=AUTH_TIMEOUT_SECONDS)
        return _AuthMessage.model_validate(raw)
    except TimeoutError:
        await websocket.close(code=4401, reason="Authentication timed out")
        return None
    except WebSocketDisconnect:
        return None
    except (ValidationError, ValueError):
        await websocket.close(code=1003, reason="Expected a {type, token} auth message first")
        return None


async def _receive_audio(websocket: WebSocket, bridge: LiveVoiceBridge) -> None:
    while True:
        message = await websocket.receive()
        if message["type"] == "websocket.disconnect":
            return
        if (data := message.get("bytes")) is not None:
            bridge.send_audio(data)
        elif (text := message.get("text")) is not None:
            payload = json.loads(text)
            if payload.get("type") == "end_utterance":
                bridge.end_utterance()
            elif payload.get("type") == "close":
                return


async def _forward_agent_events(websocket: WebSocket, bridge: LiveVoiceBridge) -> None:
    async for part in bridge.events():
        if part.inline_data is not None and part.inline_data.data is not None:
            await websocket.send_bytes(part.inline_data.data)
        elif part.text:
            await websocket.send_text(json.dumps({"type": "transcript", "text": part.text}))
