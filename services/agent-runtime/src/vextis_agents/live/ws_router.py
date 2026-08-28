import asyncio
import json
import logging
from collections.abc import Callable
from datetime import UTC, datetime
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

# Reuses the code the browser already maps to "session token expired or invalid".
CLOSE_UNAUTHORIZED = 4401
CLOSE_MESSAGE_TOO_BIG = 1009

Clock = Callable[[], datetime]


class _AuthMessage(BaseModel):
    type: str
    token: str


def create_live_router(
    settings: Settings,
    session_client: EnterpriseCoreLiveSessionClient | None = None,
    bridge_factory: Callable[[Settings, str, str], LiveVoiceBridge] | None = None,
    clock: Clock | None = None,
) -> APIRouter:
    """
    The browser opens this WebSocket after Enterprise Core authorizes a Live
    session (createLiveSession). Its first frame must be the session token —
    never a query-string token, which would otherwise leak into access logs.

    Enterprise Core stays the authority on how long that session may run: the
    expiresAt it returns from validation is enforced here as a hard deadline on
    the connection itself, so a socket that is still streaming when the session
    expires is closed and its bridge torn down rather than running on.
    """
    router = APIRouter()
    client = session_client or EnterpriseCoreLiveSessionClient(settings)
    make_bridge = bridge_factory or LiveVoiceBridge
    now = clock or (lambda: datetime.now(UTC))

    @router.websocket("/v1/live/{session_id}")
    async def live_session(websocket: WebSocket, session_id: str) -> None:
        await websocket.accept()
        correlation_id = str(uuid4())

        auth_message = await _receive_auth_message(websocket, settings)
        if auth_message is None:
            return
        if auth_message.type != "auth":
            await websocket.close(
                code=CLOSE_UNAUTHORIZED, reason="First message must be {type: auth, token}"
            )
            return

        try:
            validation = await client.validate(session_id, auth_message.token, correlation_id)
        except LiveSessionValidationError:
            logger.warning("Could not reach Enterprise Core to validate session %s", session_id)
            await websocket.close(code=1011, reason="Could not validate the session")
            return

        if not validation.valid or validation.conversation_id is None:
            await websocket.close(
                code=CLOSE_UNAUTHORIZED, reason="Invalid or expired session token"
            )
            return

        budget = _remaining_seconds(validation.expires_at, now(), settings.live_max_session_seconds)
        if budget is None:
            # Fail closed: without a usable expiry from Core there is no bound on
            # how long this connection may hold the single Cloud Run instance.
            logger.warning("Session %s was validated without a usable expiresAt", session_id)
            await websocket.close(
                code=CLOSE_UNAUTHORIZED, reason="Live session has no enforceable expiry"
            )
            return
        if budget <= 0:
            await websocket.close(code=CLOSE_UNAUTHORIZED, reason="Live session expired")
            return

        bridge = make_bridge(settings, validation.tenant_id or "", validation.conversation_id)
        forward_task = asyncio.create_task(_forward_agent_events(websocket, bridge))
        expired = False
        try:
            await asyncio.wait_for(_receive_audio(websocket, bridge, settings), timeout=budget)
        except TimeoutError:
            expired = True
            logger.info("Closing Live session %s: Enterprise Core expiry reached", session_id)
        except WebSocketDisconnect:
            pass
        finally:
            forward_task.cancel()
            # Awaiting the cancellation matters: it lets the ADK run_live
            # generator unwind and release its audio queue before the bridge is
            # closed underneath it.
            await asyncio.gather(forward_task, return_exceptions=True)
            await bridge.close()
            if _still_open(websocket):
                if expired:
                    await websocket.close(code=CLOSE_UNAUTHORIZED, reason="Live session expired")
                else:
                    await websocket.close()

    return router


def _still_open(websocket: WebSocket) -> bool:
    """True only while neither side has sent a close frame yet."""
    return (
        websocket.client_state == WebSocketState.CONNECTED
        and websocket.application_state == WebSocketState.CONNECTED
    )


def _remaining_seconds(
    expires_at: str | None, reference: datetime, cap_seconds: int
) -> float | None:
    """
    Seconds this connection may run: whatever Enterprise Core's expiresAt allows,
    never more than the deployment's own ceiling. None means the expiry could not
    be established and the connection must be refused.
    """
    deadline = _parse_instant(expires_at)
    if deadline is None:
        return None
    remaining = (deadline - reference).total_seconds()
    if cap_seconds > 0:
        remaining = min(remaining, float(cap_seconds))
    return remaining


def _parse_instant(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo is not None else parsed.replace(tzinfo=UTC)


async def _receive_auth_message(websocket: WebSocket, settings: Settings) -> _AuthMessage | None:
    try:
        message = await asyncio.wait_for(
            websocket.receive(), timeout=settings.live_auth_timeout_seconds
        )
    except TimeoutError:
        await websocket.close(code=CLOSE_UNAUTHORIZED, reason="Authentication timed out")
        return None
    except WebSocketDisconnect:
        return None

    if message["type"] == "websocket.disconnect":
        return None

    raw = message.get("text")
    if raw is None:
        await websocket.close(code=1003, reason="Expected a {type, token} auth message first")
        return None
    if len(raw) > settings.live_max_text_frame_bytes:
        await websocket.close(code=CLOSE_MESSAGE_TOO_BIG, reason="Auth frame too large")
        return None

    try:
        return _AuthMessage.model_validate(json.loads(raw))
    except (ValidationError, ValueError):
        await websocket.close(code=1003, reason="Expected a {type, token} auth message first")
        return None


async def _receive_audio(websocket: WebSocket, bridge: LiveVoiceBridge, settings: Settings) -> None:
    while True:
        message = await websocket.receive()
        if message["type"] == "websocket.disconnect":
            return
        if (data := message.get("bytes")) is not None:
            if len(data) > settings.live_max_audio_frame_bytes:
                await websocket.close(code=CLOSE_MESSAGE_TOO_BIG, reason="Audio frame too large")
                return
            bridge.send_audio(data)
        elif (text := message.get("text")) is not None:
            if len(text) > settings.live_max_text_frame_bytes:
                await websocket.close(code=CLOSE_MESSAGE_TOO_BIG, reason="Text frame too large")
                return
            try:
                payload = json.loads(text)
            except ValueError:
                continue
            if not isinstance(payload, dict):
                continue
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
