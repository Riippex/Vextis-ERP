import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.live.session_client import (
    EnterpriseCoreLiveSessionClient,
    LiveSessionValidationError,
)

SESSION_ID = "2a6e5e2b-1c8a-4a9e-9b0a-6a2c1d10ab12"


def settings() -> Settings:
    return Settings(
        enterprise_core_url="http://enterprise-core.test",
        agent_tools_token=SecretStr("test-service-token"),
        coordinator_agent_id="coordinator-agent",
    )


@pytest.mark.asyncio
async def test_validate_sends_the_service_bearer_and_session_token() -> None:
    captured: httpx.Request | None = None

    def respond(request: httpx.Request) -> httpx.Response:
        nonlocal captured
        captured = request
        return httpx.Response(
            200,
            json={
                "valid": True,
                "tenantId": "demo-tenant",
                "conversationId": "6b1a6e4a-2f0a-4e3b-8f0a-9b8b6a2c1d10",
                "expiresAt": "2026-08-25T12:05:00Z",
            },
        )

    client = EnterpriseCoreLiveSessionClient(settings(), httpx.MockTransport(respond))
    validation = await client.validate(SESSION_ID, "opaque-token", "corr-001")

    assert validation.valid is True
    assert validation.tenant_id == "demo-tenant"
    assert validation.conversation_id == "6b1a6e4a-2f0a-4e3b-8f0a-9b8b6a2c1d10"
    assert captured is not None
    assert captured.headers["Authorization"] == "Bearer test-service-token"
    assert captured.headers["X-Agent-Id"] == "coordinator-agent"
    assert captured.headers["X-Live-Session-Token"] == "opaque-token"
    assert captured.url.path.endswith(f"/live-sessions/{SESSION_ID}/validate")


@pytest.mark.asyncio
async def test_validate_returns_invalid_on_a_non_200_response() -> None:
    client = EnterpriseCoreLiveSessionClient(
        settings(), httpx.MockTransport(lambda request: httpx.Response(401))
    )

    validation = await client.validate(SESSION_ID, "wrong-token", "corr-001")

    assert validation.valid is False


@pytest.mark.asyncio
async def test_validate_raises_when_enterprise_core_is_unreachable() -> None:
    def respond(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    client = EnterpriseCoreLiveSessionClient(settings(), httpx.MockTransport(respond))

    with pytest.raises(LiveSessionValidationError):
        await client.validate(SESSION_ID, "opaque-token", "corr-001")
