import httpx
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.tools.core_api.planning import GoogleIdentityTokenProvider, IdentityTokenProvider


class LiveSessionValidation(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    valid: bool
    tenant_id: str | None = Field(default=None, alias="tenantId")
    conversation_id: str | None = Field(default=None, alias="conversationId")
    expires_at: str | None = Field(default=None, alias="expiresAt")


class LiveSessionValidationError(RuntimeError):
    """Enterprise Core could not be reached to validate the session token."""


class EnterpriseCoreLiveSessionClient:
    """
    Asks Enterprise Core whether a Live session token the browser presented is
    genuine, mirroring EnterpriseCorePlanningClient's auth header pattern
    (static service token + Cloud Run identity token for the private hop).
    """

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
        identity_token_provider: IdentityTokenProvider | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required to validate Live sessions")
        self._base_url = settings.enterprise_core_url.rstrip("/")
        self._service_token = settings.agent_tools_token.get_secret_value()
        self._agent_id = settings.coordinator_agent_id
        self._transport = transport
        self._identity_token_provider = identity_token_provider
        if settings.enterprise_core_audience and identity_token_provider is None:
            self._identity_token_provider = GoogleIdentityTokenProvider(
                settings.enterprise_core_audience
            )

    async def validate(
        self, session_id: str, presented_token: str, correlation_id: str
    ) -> LiveSessionValidation:
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Agent-Id": self._agent_id,
            "X-Correlation-Id": correlation_id,
            "X-Live-Session-Token": presented_token,
        }
        if self._identity_token_provider is not None:
            try:
                identity_token = await self._identity_token_provider()
            except Exception as exception:
                raise LiveSessionValidationError(
                    "Cloud Run identity token could not be obtained"
                ) from exception
            headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"

        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=3.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/live-sessions/{session_id}/validate",
                    headers=headers,
                )
        except httpx.HTTPError as exception:
            raise LiveSessionValidationError("Enterprise Core could not be reached") from exception

        if response.status_code != 200:
            return LiveSessionValidation(valid=False)
        return LiveSessionValidation.model_validate(response.json())
