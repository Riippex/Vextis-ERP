import sys
import types
from uuid import UUID

import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.crm.asset_generator import (
    EnterpriseCoreProposalAssetClient,
    ProposalAssetGenerator,
    ProposalAssetUploadError,
    RegisteredProposalAsset,
)
from vextis_agents.crm.imagen import (
    MOCK_MODEL_ID,
    MOCK_PNG_BYTES,
    ImagenClient,
    ImagenGenerationResult,
    redact_prompt,
)
from vextis_agents.tools.core_api.planning import CoreToolRejectedError

_UploadStore = dict[tuple[str, str], tuple[bytes, str | None]]


class _FakeBlob:
    def __init__(self, bucket_name: str, object_name: str, store: _UploadStore) -> None:
        self._bucket_name = bucket_name
        self._object_name = object_name
        self._store = store

    def upload_from_string(self, data: bytes, content_type: str | None = None) -> None:
        self._store[(self._bucket_name, self._object_name)] = (data, content_type)


class _FakeBucket:
    def __init__(self, name: str, store: _UploadStore) -> None:
        self._name = name
        self._store = store

    def blob(self, object_name: str) -> _FakeBlob:
        return _FakeBlob(self._name, object_name, self._store)


class _FakeStorageClient:
    def __init__(self) -> None:
        self.uploads: dict[tuple[str, str], tuple[bytes, str | None]] = {}

    def bucket(self, name: str) -> _FakeBucket:
        return _FakeBucket(name, self.uploads)


class _FailingBlob:
    def upload_from_string(self, data: bytes, content_type: str | None = None) -> None:
        raise OSError("network unreachable")


class _FailingStorageClient:
    def bucket(self, name: str) -> "_FailingStorageClient":
        return self

    def blob(self, object_name: str) -> _FailingBlob:
        return _FailingBlob()


def test_redact_prompt_removes_sensitive_data() -> None:
    prompt = (
        "Create 3D chair visual for client test@acme.com "
        "with card 4111-2222-3333-4444 and Bearer token1234567890"
    )
    redacted = redact_prompt(prompt)
    assert "[REDACTED_EMAIL]" in redacted
    assert "test@acme.com" not in redacted
    assert "[REDACTED_CARD]" in redacted
    assert "4111-2222-3333-4444" not in redacted
    assert "[REDACTED_TOKEN]" in redacted
    assert "token1234567890" not in redacted


def test_imagen_client_mock_generation() -> None:
    settings = Settings(
        agent_tools_token=SecretStr("test-token"),
        imagen_enabled=True,
        imagen_mock_enabled=True,
        imagen_model="imagen-3.0-generate-002",
    )
    client = ImagenClient(settings)
    result = client.generate_image("A futuristic office setup with sleek metallic desk")

    assert isinstance(result, ImagenGenerationResult)
    assert result.image_bytes == MOCK_PNG_BYTES
    assert result.mime_type == "image/png"
    # Provenance must record the mock, never the real Imagen model id.
    assert result.model_id == MOCK_MODEL_ID
    assert result.model_id != "imagen-3.0-generate-002"
    assert result.ai_label == "AI-Generated Proposal Concept"
    assert "futuristic office" in result.prompt_summary


def test_imagen_client_requires_project_when_mock_disabled() -> None:
    settings = Settings(
        agent_tools_token=SecretStr("test-token"),
        imagen_enabled=True,
        imagen_mock_enabled=False,
        google_cloud_project=None,
    )
    client = ImagenClient(settings)

    with pytest.raises(RuntimeError, match="GOOGLE_CLOUD_PROJECT"):
        client.generate_image("A futuristic office setup")


def test_imagen_client_propagates_vertex_failure_without_mock_fallback(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    fake_vertexai = types.ModuleType("vertexai")
    fake_vertexai.init = lambda **kwargs: None  # type: ignore[attr-defined]
    fake_vision_models = types.ModuleType("vertexai.preview.vision_models")

    class _FailingModel:
        @staticmethod
        def from_pretrained(model_id: str) -> "_FailingModel":
            raise RuntimeError("Vertex AI Imagen is unreachable")

    fake_vision_models.ImageGenerationModel = _FailingModel  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "vertexai", fake_vertexai)
    monkeypatch.setitem(sys.modules, "vertexai.preview.vision_models", fake_vision_models)

    settings = Settings(
        agent_tools_token=SecretStr("test-token"),
        imagen_enabled=True,
        imagen_mock_enabled=False,
        google_cloud_project="demo-project",
    )
    client = ImagenClient(settings)

    with pytest.raises(RuntimeError, match="Vertex AI Imagen is unreachable"):
        client.generate_image("A futuristic office setup")


@pytest.mark.asyncio
async def test_proposal_asset_client_registers_asset() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/agent-tools/v1/crm/quotes/quote-001/assets"
        assert request.headers["Authorization"] == "Bearer service-token-xyz"
        assert request.headers["X-Tenant-Id"] == "demo-tenant"
        assert request.headers["X-Agent-Id"] == "vextis_crm_agent"
        assert request.headers["X-Correlation-Id"] == "corr-123"
        assert request.headers["Idempotency-Key"] == "idemp-001"
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": "quote-001",
                "storageUri": "gs://vextis-erp-hackathon-assets/proposals/demo-tenant/quote-001_abc123.png",
                "mediaType": "IMAGE",
                "modelId": "imagen-3.0-generate-002",
                "promptSummary": "3D render of ergonomic chair",
                "aiLabel": "AI-Generated Proposal Concept",
                "createdAt": "2026-08-28T16:00:00Z",
            },
        )

    transport = httpx.MockTransport(handler)
    client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-123",
        transport=transport,
    )
    assert client.tenant_id == "demo-tenant"

    asset = await client.register_asset(
        quote_id="quote-001",
        storage_uri="gs://vextis-erp-hackathon-assets/proposals/demo-tenant/quote-001_abc123.png",
        media_type="IMAGE",
        model_id="imagen-3.0-generate-002",
        prompt_summary="3D render of ergonomic chair",
        ai_label="AI-Generated Proposal Concept",
        idempotency_key="idemp-001",
    )

    assert isinstance(asset, RegisteredProposalAsset)
    assert asset.id == UUID("11223344-5566-7788-99aa-bbccddeeff00")
    assert asset.quote_id == "quote-001"
    assert asset.media_type == "IMAGE"
    assert asset.model_id == "imagen-3.0-generate-002"


def test_proposal_asset_generator_rejects_tenant_mismatch() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
    )
    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(lambda request: httpx.Response(500)),
    )

    with pytest.raises(ValueError, match="tenant"):
        ProposalAssetGenerator(settings=settings, tenant_id="other-tenant", core_client=core_client)


@pytest.mark.asyncio
async def test_proposal_asset_generator_uploads_then_registers() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": "quote-002",
                "storageUri": "gs://test-assets-bucket/proposals/demo-tenant/quote-002_xxx.png",
                "mediaType": "IMAGE",
                "modelId": MOCK_MODEL_ID,
                "promptSummary": "Executive board meeting table design",
                "aiLabel": "AI-Generated Proposal Concept",
                "createdAt": "2026-08-28T16:00:00Z",
            },
        )

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-456",
        transport=httpx.MockTransport(handler),
    )
    fake_storage = _FakeStorageClient()
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=fake_storage,
    )

    result = await generator.generate_and_register(
        quote_id="quote-002",
        prompt="Executive board meeting table design",
        idempotency_key="idemp-002",
    )

    assert result.quote_id == "quote-002"
    assert result.model_id == MOCK_MODEL_ID
    assert result.ai_label == "AI-Generated Proposal Concept"

    # The image must actually have been written before registration happened.
    assert len(fake_storage.uploads) == 1
    (bucket_name, object_name), (data, content_type) = next(iter(fake_storage.uploads.items()))
    assert bucket_name == "test-assets-bucket"
    assert object_name.startswith("proposals/")
    assert "quote-002" in object_name
    assert data == MOCK_PNG_BYTES
    assert content_type == "image/png"


@pytest.mark.asyncio
async def test_proposal_asset_generator_does_not_register_when_upload_fails() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )
    register_called = False

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal register_called
        register_called = True
        return httpx.Response(201, json={})

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=_FailingStorageClient(),
    )

    with pytest.raises(ProposalAssetUploadError):
        await generator.generate_and_register(
            quote_id="quote-003",
            prompt="Executive board meeting table design",
            idempotency_key="idemp-003",
        )

    assert not register_called


@pytest.mark.asyncio
async def test_proposal_asset_client_handles_rejections() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, text="Agent not authorized")

    client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-123",
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(CoreToolRejectedError):
        await client.register_asset(
            quote_id="quote-001",
            storage_uri="gs://bucket/asset.png",
            media_type="IMAGE",
            model_id="imagen-3.0-generate-002",
            prompt_summary="Chair",
            ai_label="AI-Generated",
            idempotency_key="idemp-001",
        )
