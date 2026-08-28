import logging
from uuid import UUID, uuid4

import httpx
from pydantic import BaseModel, ConfigDict, Field

from vextis_agents.app.config import Settings
from vextis_agents.crm.imagen import ImagenClient, ImagenGenerationResult
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
    GoogleIdentityTokenProvider,
    IdentityTokenProvider,
)

logger = logging.getLogger(__name__)


class RegisteredProposalAsset(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: UUID
    quote_id: str = Field(alias="quoteId")
    storage_uri: str = Field(alias="storageUri")
    media_type: str = Field(alias="mediaType")
    model_id: str = Field(alias="modelId")
    prompt_summary: str = Field(alias="promptSummary")
    ai_label: str = Field(alias="aiLabel")
    created_at: str = Field(alias="createdAt")


class EnterpriseCoreProposalAssetClient:
    """Authenticated client for registering quote/proposal assets with Enterprise Core."""

    def __init__(
        self,
        settings: Settings,
        tenant_id: str,
        correlation_id: str | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
        identity_token_provider: IdentityTokenProvider | None = None,
    ) -> None:
        if settings.agent_tools_token is None:
            raise ValueError("VEXTIS_AGENT_TOOLS_TOKEN is required for proposal asset registration")
        if not tenant_id.strip():
            raise ValueError("A trusted tenant is required for proposal asset registration")
        self._base_url = settings.enterprise_core_url.rstrip("/")
        self._tenant_id = tenant_id
        self._correlation_id = correlation_id or str(uuid4())
        self._service_token = settings.agent_tools_token.get_secret_value()
        self._crm_agent_id = settings.crm_agent_id
        self._transport = transport
        self._identity_token_provider = identity_token_provider
        if settings.enterprise_core_audience and identity_token_provider is None:
            self._identity_token_provider = GoogleIdentityTokenProvider(
                settings.enterprise_core_audience
            )

    async def register_asset(
        self,
        quote_id: str,
        storage_uri: str,
        media_type: str,
        model_id: str,
        prompt_summary: str,
        ai_label: str,
        idempotency_key: str,
    ) -> RegisteredProposalAsset:
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": self._tenant_id,
            "X-Agent-Id": self._crm_agent_id,
            "X-Correlation-Id": self._correlation_id,
            "Idempotency-Key": idempotency_key,
        }
        if self._identity_token_provider is not None:
            try:
                identity_token = await self._identity_token_provider()
                headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"
            except Exception as exception:
                raise CoreToolUnavailableError(
                    "Cloud Run identity token could not be obtained"
                ) from exception

        payload = {
            "storageUri": storage_uri,
            "mediaType": media_type,
            "modelId": model_id,
            "promptSummary": prompt_summary,
            "aiLabel": ai_label,
        }

        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(15.0, connect=5.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/crm/quotes/{quote_id}/assets",
                    headers=headers,
                    json=payload,
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception

        if response.status_code in (200, 201):
            return RegisteredProposalAsset.model_validate(response.json())
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected proposal asset registration with {response.status_code}"
        )


class ProposalAssetGenerator:
    """Orchestrates image generation with Imagen 3 and registers the asset in Core."""

    def __init__(
        self,
        settings: Settings,
        core_client: EnterpriseCoreProposalAssetClient,
        imagen_client: ImagenClient | None = None,
    ) -> None:
        self._settings = settings
        self._core_client = core_client
        self._imagen = imagen_client or ImagenClient(settings)

    async def generate_and_register(
        self,
        quote_id: str,
        prompt: str,
        idempotency_key: str,
    ) -> RegisteredProposalAsset:
        # Step 1: Generate the visual asset via Imagen 3
        generation: ImagenGenerationResult = self._imagen.generate_image(prompt)

        # Step 2: Form Cloud Storage URI
        asset_uid = uuid4().hex[:12]
        bucket = self._settings.gcs_proposal_assets_bucket
        storage_uri = f"gs://{bucket}/proposals/{self._core_client._tenant_id}/{quote_id}_{asset_uid}.png"

        # Step 3: Register asset with Enterprise Core
        return await self._core_client.register_asset(
            quote_id=quote_id,
            storage_uri=storage_uri,
            media_type="IMAGE",
            model_id=generation.model_id,
            prompt_summary=generation.prompt_summary,
            ai_label=generation.ai_label,
            idempotency_key=idempotency_key,
        )
