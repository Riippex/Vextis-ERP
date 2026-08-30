import asyncio
import sys
import types
from uuid import UUID

import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.crm.asset_generator import (
    EnterpriseCoreProposalAssetClient,
    PreflightProposalAsset,
    ProposalAssetGenerator,
    ProposalAssetTimeoutError,
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
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
)

_UploadStore = dict[tuple[str, str], tuple[bytes, str | None]]

TEST_QUOTE_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
TEST_QUOTE_UUID = UUID(TEST_QUOTE_ID)


class _FakeBlob:
    def __init__(self, bucket_name: str, object_name: str, store: _UploadStore) -> None:
        self._bucket_name = bucket_name
        self._object_name = object_name
        self._store = store

    def upload_from_string(
        self,
        data: bytes,
        content_type: str | None = None,
        if_generation_match: int | None = None,
    ) -> None:
        self._store[(self._bucket_name, self._object_name)] = (data, content_type)

    def delete(self) -> None:
        self._store.pop((self._bucket_name, self._object_name), None)


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
    def upload_from_string(
        self,
        data: bytes,
        content_type: str | None = None,
        if_generation_match: int | None = None,
    ) -> None:
        raise OSError("network unreachable")

    def delete(self) -> None:
        pass


class _FailingBucket:
    def blob(self, object_name: str) -> _FailingBlob:
        return _FailingBlob()


class _FailingStorageClient:
    def bucket(self, name: str) -> _FailingBucket:
        return _FailingBucket()


def test_redact_prompt_removes_sensitive_data() -> None:
    prompt = (
        "Create 3D chair visual for cliente: Juan Perez with email test@acme.com, "
        "phone +57 300 123 4567, address Calle 100 # 15-20, NIT 900123456-1, "
        "card 4111-2222-3333-4444, apiKey=AIzaSyD-1234567890abcdef and Bearer token1234567890"
    )
    redacted = redact_prompt(prompt)
    assert "[REDACTED_EMAIL]" in redacted
    assert "test@acme.com" not in redacted
    assert "[REDACTED_CARD]" in redacted
    assert "4111-2222-3333-4444" not in redacted
    assert "[REDACTED_TOKEN]" in redacted
    assert "token1234567890" not in redacted
    assert "[REDACTED_PHONE]" in redacted
    assert "300 123 4567" not in redacted
    assert "[REDACTED_ADDRESS]" in redacted
    assert "Calle 100" not in redacted
    assert "[REDACTED_NAME]" in redacted
    assert "Juan Perez" not in redacted
    assert "[REDACTED_ID]" in redacted
    assert "900123456" not in redacted


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
        assert request.url.path == f"/internal/agent-tools/v1/crm/quotes/{TEST_QUOTE_ID}/assets"
        assert request.headers["Authorization"] == "Bearer service-token-xyz"
        assert request.headers["X-Tenant-Id"] == "demo-tenant"
        assert request.headers["X-Agent-Id"] == "vextis_crm_agent"
        assert request.headers["X-Correlation-Id"] == "corr-123"
        assert request.headers["Idempotency-Key"] == "idemp-001"
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": TEST_QUOTE_ID,
                "storageUri": (
                    f"gs://vextis-erp-hackathon-assets/proposals/demo-tenant/{TEST_QUOTE_ID}_abc123.png"
                ),
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
        quote_id=TEST_QUOTE_ID,
        storage_uri=f"gs://vextis-erp-hackathon-assets/proposals/demo-tenant/{TEST_QUOTE_ID}_abc123.png",
        media_type="IMAGE",
        model_id="imagen-3.0-generate-002",
        prompt_summary="3D render of ergonomic chair",
        ai_label="AI-Generated Proposal Concept",
        idempotency_key="idemp-001",
    )

    assert isinstance(asset, RegisteredProposalAsset)
    assert asset.id == UUID("11223344-5566-7788-99aa-bbccddeeff00")
    assert asset.quote_id == TEST_QUOTE_ID
    assert asset.media_type == "IMAGE"
    assert asset.model_id == "imagen-3.0-generate-002"


@pytest.mark.asyncio
async def test_proposal_asset_client_preflight_checks_authorization() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        expected_path = f"/internal/agent-tools/v1/crm/quotes/{TEST_QUOTE_ID}/assets/preflight"
        assert request.url.path == expected_path
        return httpx.Response(
            200,
            json={
                "quoteId": TEST_QUOTE_ID,
                "authorized": True,
                "tenantPrefix": "proposals/deadbeef",
                "correlationId": "corr-123",
            },
        )

    client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-123",
        transport=httpx.MockTransport(handler),
    )
    preflight = await client.preflight_asset(TEST_QUOTE_ID)
    assert isinstance(preflight, PreflightProposalAsset)
    assert preflight.quote_id == TEST_QUOTE_ID
    assert preflight.authorized is True


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
        ProposalAssetGenerator(
            settings=settings, tenant_id="other-tenant", core_client=core_client
        )


@pytest.mark.asyncio
async def test_proposal_asset_generator_rejects_invalid_uuid_without_generation() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
    )
    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(lambda request: httpx.Response(500)),
    )
    fake_storage = _FakeStorageClient()
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=fake_storage,
    )

    with pytest.raises(ValueError, match="must be a valid UUID"):
        await generator.generate_and_register(
            quote_id="not-a-valid-uuid",
            prompt="A sleek table",
        )

    assert len(fake_storage.uploads) == 0


@pytest.mark.asyncio
async def test_proposal_asset_generator_aborts_when_preflight_fails_zero_spend() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/preflight"):
            return httpx.Response(404, text="Quote not found for tenant")
        return httpx.Response(201, json={})

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )
    fake_storage = _FakeStorageClient()
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=fake_storage,
    )

    with pytest.raises(CoreToolRejectedError, match="No quote or order found"):
        await generator.generate_and_register(
            quote_id=TEST_QUOTE_ID,
            prompt="A sleek desk",
        )

    # Zero uploads to storage
    assert len(fake_storage.uploads) == 0


@pytest.mark.asyncio
async def test_proposal_asset_generator_deterministic_idempotency_and_upload() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/preflight"):
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                },
            )
        import json

        body = json.loads(request.content.decode("utf-8"))
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": TEST_QUOTE_ID,
                "storageUri": body["storageUri"],
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

    result1 = await generator.generate_and_register(
        quote_id=TEST_QUOTE_ID,
        prompt="Executive board meeting table design",
    )
    (bucket1, obj1), (data1, _) = next(iter(fake_storage.uploads.items()))

    # Second invocation with same inputs produces identical deterministic object name
    result2 = await generator.generate_and_register(
        quote_id=TEST_QUOTE_ID,
        prompt="Executive board meeting table design",
    )

    assert result1.quote_id == TEST_QUOTE_ID
    assert result2.quote_id == TEST_QUOTE_ID
    assert len(fake_storage.uploads) == 1
    assert bucket1 == "test-assets-bucket"
    assert TEST_QUOTE_ID in obj1
    assert data1 == MOCK_PNG_BYTES


@pytest.mark.asyncio
async def test_proposal_asset_generator_compensates_orphaned_blob_on_core_failure() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/preflight"):
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                },
            )
        # Registration fails with definitive 400 Bad Request error
        return httpx.Response(400, text="Bad Request: payload invalid")

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

    with pytest.raises(CoreToolRejectedError):
        await generator.generate_and_register(
            quote_id=TEST_QUOTE_ID,
            prompt="Executive board meeting table design",
        )

    # Blob was uploaded and then cleaned up via compensation
    assert len(fake_storage.uploads) == 0


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
        if request.url.path.endswith("/preflight"):
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                },
            )
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
            quote_id=TEST_QUOTE_ID,
            prompt="Executive board meeting table design",
        )

    assert not register_called


@pytest.mark.asyncio
async def test_proposal_asset_preflight_idempotent_replay_skips_imagen_and_upload() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    existing_asset_json = {
        "id": "11223344-5566-7788-99aa-bbccddeeff00",
        "quoteId": TEST_QUOTE_ID,
        "storageUri": "gs://test-assets-bucket/proposals/deadbeef/chair.png",
        "mediaType": "IMAGE",
        "modelId": "imagen-3.0-generate-002",
        "promptSummary": "Executive chair",
        "aiLabel": "AI-Generated",
        "createdAt": "2026-08-28T16:00:00Z",
    }

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/preflight"):
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                    "alreadyRegistered": True,
                    "existingAsset": existing_asset_json,
                },
            )
        pytest.fail("Register endpoint must NOT be called on preflight replay")

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-456",
        transport=httpx.MockTransport(handler),
    )
    fake_storage = _FakeStorageClient()
    fake_imagen = ImagenClient(settings)

    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        imagen_client=fake_imagen,
        storage_client=fake_storage,
    )

    result = await generator.generate_and_register(
        quote_id=TEST_QUOTE_ID,
        prompt="Executive chair",
        idempotency_key="idemp-existing-123",
    )

    assert str(result.id) == "11223344-5566-7788-99aa-bbccddeeff00"
    # Zero uploads happened because it was already registered
    assert len(fake_storage.uploads) == 0


@pytest.mark.asyncio
async def test_proposal_asset_generator_preserves_blob_on_transient_or_timeout_failure() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/preflight"):
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                },
            )
        return httpx.Response(500, text="Internal Server Error")

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

    with pytest.raises(CoreToolUnavailableError):
        await generator.generate_and_register(
            quote_id=TEST_QUOTE_ID,
            prompt="Executive board meeting table design",
        )

    assert len(fake_storage.uploads) == 1


@pytest.mark.asyncio
async def test_proposal_asset_timeout_returns_structured_error() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "quoteId": TEST_QUOTE_ID,
                "authorized": True,
                "tenantPrefix": "proposals/deadbeef",
                "correlationId": "corr-456",
                "status": "RESERVED",
                "owner": True,
            },
        )

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-456",
        transport=httpx.MockTransport(handler),
    )

    class SlowImagenClient(ImagenClient):
        def generate_image(self, prompt: str) -> ImagenGenerationResult:
            import time

            time.sleep(0.5)
            return ImagenGenerationResult(
                image_bytes=b"fake",
                mime_type="image/png",
                model_id="test",
                prompt_summary=prompt,
                ai_label="AI-Generated",
            )

    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        imagen_client=SlowImagenClient(settings),
        storage_client=_FakeStorageClient(),
        generation_timeout_seconds=0.05,
    )

    with pytest.raises(ProposalAssetTimeoutError) as exc_info:
        await generator.generate_and_register(quote_id=TEST_QUOTE_ID, prompt="Slow")
    assert exc_info.value.error_code == "IMAGEN_TIMEOUT"


@pytest.mark.asyncio
async def test_same_key_different_prompt_preflight_returns_409_conflict() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(409, json={"message": "Conflict"})

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-456",
        transport=httpx.MockTransport(handler),
    )
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=_FakeStorageClient(),
    )

    with pytest.raises(CoreToolRejectedError):
        await generator.generate_and_register(
            quote_id=TEST_QUOTE_ID,
            prompt="New prompt",
            idempotency_key="key",
        )


@pytest.mark.asyncio
async def test_concurrent_request_waits_and_retrieves_completed_asset() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "quoteId": TEST_QUOTE_ID,
                "authorized": True,
                "status": "COMPLETED",
                "alreadyRegistered": True,
                "existingAsset": {
                    "id": "11223344-5566-7788-99aa-bbccddeeff00",
                    "quoteId": TEST_QUOTE_ID,
                    "storageUri": "gs://bucket/asset.png",
                    "mediaType": "IMAGE",
                    "modelId": "imagen-3",
                    "promptSummary": "Chair",
                    "aiLabel": "AI-Generated",
                    "createdAt": "2026-08-28T16:00:00Z",
                },
            },
        )

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=_FakeStorageClient(),
    )

    result = await generator.generate_and_register(quote_id=TEST_QUOTE_ID, prompt="Chair")
    assert str(result.id) == "11223344-5566-7788-99aa-bbccddeeff00"


@pytest.mark.asyncio
async def test_registration_409_conflict_does_not_delete_blob() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        if "/preflight" in str(request.url):
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "status": "RESERVED",
                    "owner": True,
                },
            )
        return httpx.Response(409, json={"message": "Conflict"})

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        transport=httpx.MockTransport(handler),
    )
    fake_storage = _FakeStorageClient()
    generator = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        storage_client=fake_storage,
    )

    with pytest.raises(CoreToolRejectedError):
        await generator.generate_and_register(
            quote_id=TEST_QUOTE_ID,
            prompt="Chair",
            idempotency_key="key",
        )

    assert len(fake_storage.uploads) == 1


@pytest.mark.asyncio
async def test_prompt_with_full_pii_canonicalized_before_preflight_no_conflict() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    raw_pii_prompt = (
        "Modern chair for customer Juan Perez email juan@acme.com phone +57 300 123 4567 "
        "card 4111-2222-3333-4444 NIT 900123456-1"
    )
    expected_redacted = redact_prompt(raw_pii_prompt).strip()

    preflight_prompt_received: list[str] = []
    register_prompt_received: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        import json

        if request.url.path.endswith("/preflight"):
            body = json.loads(request.content.decode("utf-8")) if request.content else {}
            preflight_prompt_received.append(body.get("promptSummary", ""))
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                    "status": "RESERVED",
                    "owner": True,
                    "reservationToken": "tok-pii-123",
                },
            )
        body = json.loads(request.content.decode("utf-8"))
        register_prompt_received.append(body.get("promptSummary", ""))
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": TEST_QUOTE_ID,
                "storageUri": body["storageUri"],
                "mediaType": "IMAGE",
                "modelId": MOCK_MODEL_ID,
                "promptSummary": body["promptSummary"],
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
        quote_id=TEST_QUOTE_ID,
        prompt=raw_pii_prompt,
    )

    assert result.prompt_summary == expected_redacted
    assert preflight_prompt_received == [expected_redacted]
    assert register_prompt_received == [expected_redacted]
    assert len(fake_storage.uploads) == 1


@pytest.mark.asyncio
async def test_two_concurrent_requests_same_agent_id_generates_exactly_once() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    registered_asset_json: dict[str, object] | None = None
    generation_calls = 0
    preflight_calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal registered_asset_json, preflight_calls
        import json

        if request.url.path.endswith("/preflight"):
            if registered_asset_json is not None:
                return httpx.Response(
                    200,
                    json={
                        "quoteId": TEST_QUOTE_ID,
                        "authorized": True,
                        "tenantPrefix": "proposals/deadbeef",
                        "correlationId": "corr-456",
                        "status": "COMPLETED",
                        "alreadyRegistered": True,
                        "existingAsset": registered_asset_json,
                    },
                )
            preflight_calls += 1
            is_first = preflight_calls == 1
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                    "status": "RESERVED" if is_first else "PENDING",
                    "owner": is_first,
                    "reservationToken": "tok-first" if is_first else None,
                },
            )
        # Register call
        body = json.loads(request.content.decode("utf-8"))
        registered_asset_json = {
            "id": "11223344-5566-7788-99aa-bbccddeeff00",
            "quoteId": TEST_QUOTE_ID,
            "storageUri": body["storageUri"],
            "mediaType": "IMAGE",
            "modelId": MOCK_MODEL_ID,
            "promptSummary": body["promptSummary"],
            "aiLabel": "AI-Generated Proposal Concept",
            "createdAt": "2026-08-28T16:00:00Z",
        }
        return httpx.Response(201, json=registered_asset_json)

    core_client = EnterpriseCoreProposalAssetClient(
        settings=settings,
        tenant_id="demo-tenant",
        correlation_id="corr-456",
        transport=httpx.MockTransport(handler),
    )
    fake_storage = _FakeStorageClient()

    class CountingImagenClient(ImagenClient):
        def generate_image(self, prompt: str) -> ImagenGenerationResult:
            nonlocal generation_calls
            generation_calls += 1
            return super().generate_image(prompt)

    generator1 = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        imagen_client=CountingImagenClient(settings),
        storage_client=fake_storage,
    )
    generator2 = ProposalAssetGenerator(
        settings=settings,
        tenant_id="demo-tenant",
        core_client=core_client,
        imagen_client=CountingImagenClient(settings),
        storage_client=fake_storage,
    )

    res1, res2 = await asyncio.gather(
        generator1.generate_and_register(TEST_QUOTE_ID, "Executive chair", "idemp-shared-key"),
        generator2.generate_and_register(TEST_QUOTE_ID, "Executive chair", "idemp-shared-key"),
    )

    assert str(res1.id) == "11223344-5566-7788-99aa-bbccddeeff00"
    assert str(res2.id) == "11223344-5566-7788-99aa-bbccddeeff00"
    assert generation_calls == 1
    assert len(fake_storage.uploads) == 1


@pytest.mark.asyncio
async def test_crashed_owner_permits_takeover_after_lease_expires() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
        gcs_proposal_assets_bucket="test-assets-bucket",
    )

    def handler(request: httpx.Request) -> httpx.Response:
        import json

        if request.url.path.endswith("/preflight"):
            # Core grants takeover after previous lease expired
            return httpx.Response(
                200,
                json={
                    "quoteId": TEST_QUOTE_ID,
                    "authorized": True,
                    "tenantPrefix": "proposals/deadbeef",
                    "correlationId": "corr-456",
                    "status": "RESERVED",
                    "owner": True,
                    "reservationToken": "tok-takeover-789",
                },
            )
        body = json.loads(request.content.decode("utf-8"))
        assert body.get("reservationToken") == "tok-takeover-789"
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": TEST_QUOTE_ID,
                "storageUri": body["storageUri"],
                "mediaType": "IMAGE",
                "modelId": MOCK_MODEL_ID,
                "promptSummary": body["promptSummary"],
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
        quote_id=TEST_QUOTE_ID,
        prompt="Modern conference table",
        idempotency_key="idemp-crashed-owner-recovery",
    )

    assert str(result.id) == "11223344-5566-7788-99aa-bbccddeeff00"
    assert len(fake_storage.uploads) == 1

