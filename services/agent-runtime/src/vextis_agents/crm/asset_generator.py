import asyncio
import concurrent.futures
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

# Shared process-level executor ensuring bounded concurrency across all coordinator instances
# and preventing capacity leakage if an async call times out while the underlying thread runs.
_PROCESS_IMAGEN_EXECUTOR = concurrent.futures.ThreadPoolExecutor(
    max_workers=2,
    thread_name_prefix="imagen-process-worker",
)


class ProposalAssetUploadError(Exception):
    """Raised when the generated image could not be written to Cloud Storage."""


class ProposalAssetTimeoutError(Exception):
    """Raised when proposal asset generation times out with structured error details."""

    def __init__(
        self,
        message: str = "Image generation timed out",
        retryable: bool = True,
        error_code: str = "IMAGEN_TIMEOUT",
    ) -> None:
        super().__init__(message)
        self.message = message
        self.retryable = retryable
        self.error_code = error_code


def _tenant_object_prefix(tenant_id: str) -> str:
    """Deterministic per-tenant prefix, mirroring Enterprise Core's own
    SHA-256-derived prefix so both sides agree on what a tenant-scoped
    proposal asset URI looks like."""
    digest = hashlib.sha256(tenant_id.encode("utf-8")).hexdigest()
    return digest[:24]


class _ImageBlob(Protocol):
    def upload_from_string(
        self,
        data: bytes,
        content_type: str | None = None,
        if_generation_match: int | None = None,
    ) -> object: ...
    def delete(self) -> object: ...


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


class PreflightProposalAsset(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    quote_id: str = Field(alias="quoteId")
    authorized: bool
    tenant_prefix: str = Field(alias="tenantPrefix")
    correlation_id: str = Field(alias="correlationId")
    already_registered: bool = Field(default=False, alias="alreadyRegistered")
    existing_asset: RegisteredProposalAsset | None = Field(default=None, alias="existingAsset")


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

    @property
    def correlation_id(self) -> str:
        return self._correlation_id

    async def preflight_asset(
        self, quote_id: str, idempotency_key: str | None = None
    ) -> PreflightProposalAsset:
        """Validates that quoteId exists and belongs to this tenant, caller is authorized,
        and checks if idempotency_key is already registered."""
        headers = {
            "Authorization": f"Bearer {self._service_token}",
            "X-Tenant-Id": self._tenant_id,
            "X-Agent-Id": self._crm_agent_id,
            "X-Correlation-Id": self._correlation_id,
        }
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key
        if self._identity_token_provider is not None:
            try:
                identity_token = await self._identity_token_provider()
                headers["X-Serverless-Authorization"] = f"Bearer {identity_token}"
            except Exception as exception:
                raise CoreToolUnavailableError(
                    "Cloud Run identity token could not be obtained"
                ) from exception

        try:
            async with httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(10.0, connect=5.0),
                transport=self._transport,
            ) as client:
                response = await client.post(
                    f"/internal/agent-tools/v1/crm/quotes/{quote_id}/assets/preflight",
                    headers=headers,
                )
        except httpx.HTTPError as exception:
            raise CoreToolUnavailableError("Enterprise Core could not be reached") from exception

        if response.status_code == 200:
            return PreflightProposalAsset.model_validate(response.json())
        if response.status_code == 404:
            raise CoreToolRejectedError("No quote or order found for this tenant")
        if response.status_code >= 500:
            raise CoreToolUnavailableError("Enterprise Core returned a transient failure")
        raise CoreToolRejectedError(
            f"Enterprise Core rejected proposal asset preflight with {response.status_code}"
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
    """Orchestrates image generation with Imagen 3, uploads the result to Cloud
    Storage, and registers the asset in Core only once the write is confirmed."""

    def __init__(
        self,
        settings: Settings,
        tenant_id: str,
        core_client: EnterpriseCoreProposalAssetClient,
        imagen_client: ImagenClient | None = None,
        storage_client: ImageObjectStore | None = None,
        generation_timeout_seconds: float = 30.0,
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
        self._generation_timeout_seconds = generation_timeout_seconds

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
            blob.upload_from_string(
                generation.image_bytes,
                content_type=generation.mime_type,
                if_generation_match=0,
            )
        except Exception as exception:
            # If the object was already uploaded by a previous retry, 412 is returned
            status_code = getattr(exception, "code", None) or getattr(
                exception, "status_code", None
            )
            if status_code == 412 or "PreconditionFailed" in type(exception).__name__:
                logger.info(
                    "Object gs://%s/%s already uploaded with generation match 0; continuing.",
                    bucket_name,
                    object_name,
                )
                return
            raise ProposalAssetUploadError(
                f"Failed to upload proposal asset to gs://{bucket_name}/{object_name}"
            ) from exception

    def _delete_blob(self, bucket_name: str, object_name: str) -> None:
        """Compensating action: clean up orphaned blob on definitive Core rejection."""
        try:
            blob = self._resolve_storage_client().bucket(bucket_name).blob(object_name)
            blob.delete()
            logger.info("Cleaned up orphaned storage object gs://%s/%s", bucket_name, object_name)
        except Exception as exception:
            logger.warning(
                "Failed to clean up orphaned storage object gs://%s/%s: %s",
                bucket_name,
                object_name,
                exception,
            )

    async def generate_and_register(
        self,
        quote_id: str,
        prompt: str,
        idempotency_key: str | None = None,
    ) -> RegisteredProposalAsset:
        # Step 0: Strict UUID validation
        try:
            UUID(quote_id)
        except (ValueError, TypeError, AttributeError) as exc:
            raise ValueError(f"Invalid quote_id '{quote_id}': must be a valid UUID") from exc

        # Derive stable, deterministic idempotency key
        if idempotency_key is None or not idempotency_key.strip():
            key_source = (
                f"{self._tenant_id}:{quote_id}:{prompt.strip()}:{self._core_client.correlation_id}"
            )
            idempotency_key = (
                f"proposal-asset-{hashlib.sha256(key_source.encode('utf-8')).hexdigest()[:32]}"
            )

        # Preflight verification against Enterprise Core with idempotency check
        preflight = await self._core_client.preflight_asset(
            quote_id, idempotency_key=idempotency_key
        )
        if preflight.already_registered and preflight.existing_asset is not None:
            logger.info(
                "Proposal asset already registered for quote %s and key %s; returning existing.",
                quote_id,
                idempotency_key,
            )
            return preflight.existing_asset

        # Step 1: Generate the visual asset via process-level bounded executor
        loop = asyncio.get_running_loop()
        try:
            generation: ImagenGenerationResult = await asyncio.wait_for(
                loop.run_in_executor(
                    _PROCESS_IMAGEN_EXECUTOR, self._imagen.generate_image, prompt
                ),
                timeout=self._generation_timeout_seconds,
            )
        except TimeoutError as exc:
            raise ProposalAssetTimeoutError(
                f"Imagen generation timed out after {self._generation_timeout_seconds}s",
                retryable=True,
                error_code="IMAGEN_TIMEOUT",
            ) from exc

        # Step 2: Deterministic object naming derived from tenant, quote, and idempotency key
        bucket = self._settings.gcs_proposal_assets_bucket
        if not bucket:
            raise ProposalAssetUploadError(
                "VEXTIS_GCS_PROPOSAL_ASSETS_BUCKET must be configured to store proposal assets"
            )
        tenant_prefix = _tenant_object_prefix(self._tenant_id)
        idemp_hash = hashlib.sha256(idempotency_key.encode("utf-8")).hexdigest()[:16]
        object_name = f"proposals/{tenant_prefix}/{quote_id}_{idemp_hash}.png"

        # Upload the generated bytes to Cloud Storage with if_generation_match=0
        await asyncio.to_thread(self._upload, bucket, object_name, generation)
        storage_uri = f"gs://{bucket}/{object_name}"

        # Step 3: Register the confirmed asset with Enterprise Core.
        # Only compensate (delete blob) on definitive 4xx Core rejections.
        try:
            return await self._core_client.register_asset(
                quote_id=quote_id,
                storage_uri=storage_uri,
                media_type="IMAGE",
                model_id=generation.model_id,
                prompt_summary=generation.prompt_summary,
                ai_label=generation.ai_label,
                idempotency_key=idempotency_key,
            )
        except CoreToolRejectedError:
            # Definitive rejection (4xx): compensate
            await asyncio.to_thread(self._delete_blob, bucket, object_name)
            raise
        except (CoreToolUnavailableError, Exception) as exc:
            # Ambiguous / transient error: preserve blob for reconciliation/retry
            logger.warning(
                "Core registration failed with transient or ambiguous error %s; preserving blob gs://%s/%s",
                exc,
                bucket,
                object_name,
            )
            raise
