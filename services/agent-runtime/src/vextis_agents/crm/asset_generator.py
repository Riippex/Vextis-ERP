import asyncio
import hashlib
import logging
from typing import Protocol, cast
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


class ProposalAssetUploadError(Exception):
    """Raised when the generated image could not be written to Cloud Storage."""


def _tenant_object_prefix(tenant_id: str) -> str:
    """Deterministic per-tenant prefix, mirroring Enterprise Core's own
    SHA-256-derived prefix so both sides agree on what a tenant-scoped
    proposal asset URI looks like."""
    digest = hashlib.sha256(tenant_id.encode("utf-8")).hexdigest()
    return digest[:24]


class _ImageBlob(Protocol):
    def upload_from_string(self, data: bytes, content_type: str | None = None) -> object: ...


class _ImageBucket(Protocol):
    def blob(self, object_name: str) -> _ImageBlob: ...


class ImageObjectStore(Protocol):
    """Minimal surface of google.cloud.storage.Client used to upload proposal
    assets. A Protocol so tests can supply a lightweight fake instead of a
    real Cloud Storage client."""

    def bucket(self, name: str) -> _ImageBucket: ...


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

    @property
    def tenant_id(self) -> str:
        return self._tenant_id

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
    """Orchestrates image generation with Imagen 3, uploads the result to Cloud
    Storage, and registers the asset in Core only once the write is confirmed."""

    def __init__(
        self,
        settings: Settings,
        tenant_id: str,
        core_client: EnterpriseCoreProposalAssetClient,
        imagen_client: ImagenClient | None = None,
        storage_client: ImageObjectStore | None = None,
    ) -> None:
        if tenant_id != core_client.tenant_id:
            raise ValueError(
                "ProposalAssetGenerator tenant must match the Enterprise Core client tenant"
            )
        self._settings = settings
        self._tenant_id = tenant_id
        self._core_client = core_client
        self._imagen = imagen_client or ImagenClient(settings)
        self._storage_client = storage_client

    def _resolve_storage_client(self) -> ImageObjectStore:
        if self._storage_client is None:
            import google.cloud.storage

            self._storage_client = cast(ImageObjectStore, google.cloud.storage.Client())
        return self._storage_client

    def _upload(
        self, bucket_name: str, object_name: str, generation: ImagenGenerationResult
    ) -> None:
        blob = self._resolve_storage_client().bucket(bucket_name).blob(object_name)
        try:
            blob.upload_from_string(generation.image_bytes, content_type=generation.mime_type)
        except Exception as exception:
            raise ProposalAssetUploadError(
                f"Failed to upload proposal asset to gs://{bucket_name}/{object_name}"
            ) from exception

    async def generate_and_register(
        self,
        quote_id: str,
        prompt: str,
        idempotency_key: str,
    ) -> RegisteredProposalAsset:
        # Step 1: Generate the visual asset via Imagen 3 (or an intentionally
        # enabled mock). No fallback: a Vertex AI failure propagates.
        generation: ImagenGenerationResult = self._imagen.generate_image(prompt)

        # Step 2: Upload the generated bytes to Cloud Storage and confirm the
        # write completed before anything is registered as existing.
        bucket = self._settings.gcs_proposal_assets_bucket
        if not bucket:
            raise ProposalAssetUploadError(
                "VEXTIS_GCS_PROPOSAL_ASSETS_BUCKET must be configured to store proposal assets"
            )
        asset_uid = uuid4().hex[:12]
        tenant_prefix = _tenant_object_prefix(self._tenant_id)
        object_name = f"proposals/{tenant_prefix}/{quote_id}_{asset_uid}.png"
        await asyncio.to_thread(self._upload, bucket, object_name, generation)
        storage_uri = f"gs://{bucket}/{object_name}"

        # Step 3: Register the now-confirmed asset with Enterprise Core.
        return await self._core_client.register_asset(
            quote_id=quote_id,
            storage_uri=storage_uri,
            media_type="IMAGE",
            model_id=generation.model_id,
            prompt_summary=generation.prompt_summary,
            ai_label=generation.ai_label,
            idempotency_key=idempotency_key,
        )
