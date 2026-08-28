from uuid import UUID

import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.crm.asset_generator import (
    EnterpriseCoreProposalAssetClient,
    ProposalAssetGenerator,
    RegisteredProposalAsset,
)
from vextis_agents.crm.imagen import (
    MOCK_PNG_BYTES,
    ImagenClient,
    ImagenGenerationResult,
    redact_prompt,
)
from vextis_agents.tools.core_api.planning import CoreToolRejectedError


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
    assert result.model_id == "imagen-3.0-generate-002"
    assert result.ai_label == "AI-Generated Proposal Concept"
    assert "futuristic office" in result.prompt_summary


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
                "storageUri": "gs://vextis-proposal-assets/proposals/demo-tenant/quote-001_abc123.png",
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

    asset = await client.register_asset(
        quote_id="quote-001",
        storage_uri="gs://vextis-proposal-assets/proposals/demo-tenant/quote-001_abc123.png",
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


@pytest.mark.asyncio
async def test_proposal_asset_generator_flow() -> None:
    settings = Settings(
        enterprise_core_url="http://core.internal",
        agent_tools_token=SecretStr("service-token-xyz"),
        crm_agent_id="vextis_crm_agent",
        imagen_mock_enabled=True,
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            201,
            json={
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "quoteId": "quote-002",
                "storageUri": "gs://vextis-proposal-assets/proposals/demo-tenant/quote-002_xxx.png",
                "mediaType": "IMAGE",
                "modelId": "imagen-3.0-generate-002",
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
    generator = ProposalAssetGenerator(settings=settings, core_client=core_client)

    result = await generator.generate_and_register(
        quote_id="quote-002",
        prompt="Executive board meeting table design",
        idempotency_key="idemp-002",
    )

    assert result.quote_id == "quote-002"
    assert result.model_id == "imagen-3.0-generate-002"
    assert result.ai_label == "AI-Generated Proposal Concept"


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
