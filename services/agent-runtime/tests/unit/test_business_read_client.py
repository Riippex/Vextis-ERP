from uuid import UUID

import httpx
import pytest
from pydantic import SecretStr

from vextis_agents.app.config import Settings
from vextis_agents.tools.core_api.business_reads import EnterpriseCoreBusinessReadClient
from vextis_agents.tools.core_api.planning import CoreToolRejectedError


def settings() -> Settings:
    return Settings(
        enterprise_core_url="https://core.example.test",
        agent_tools_token=SecretStr("service-secret"),
        coordinator_agent_id="coordinator-agent",
    )


@pytest.mark.asyncio
async def test_customer_lookup_sends_trusted_tenant_and_service_identity() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["Authorization"] == "Bearer service-secret"
        assert request.headers["X-Tenant-Id"] == "demo-tenant"
        assert request.headers["X-Agent-Id"] == "vextis_crm_agent"
        assert request.headers["X-Correlation-Id"] == "conversation-001"
        assert request.url.params["legalName"] == "Acme Colombia"
        return httpx.Response(
            200,
            json={
                "id": "09ec135d-9688-47de-ac71-5b8420b97488",
                "legalName": "Acme Colombia",
                "active": True,
            },
        )

    client = EnterpriseCoreBusinessReadClient(
        settings(),
        "demo-tenant",
        "conversation-001",
        transport=httpx.MockTransport(handler),
    )

    result = await client.lookup_customer("Acme Colombia")

    assert result is not None
    assert result.legal_name == "Acme Colombia"
    assert result.active is True


@pytest.mark.asyncio
async def test_stock_lookup_returns_none_for_tenant_scoped_not_found() -> None:
    client = EnterpriseCoreBusinessReadClient(
        settings(),
        "demo-tenant",
        transport=httpx.MockTransport(lambda _: httpx.Response(404)),
    )

    assert await client.get_stock("UNKNOWN-SKU") is None


@pytest.mark.asyncio
async def test_credit_lookup_parses_strict_response() -> None:
    customer_id = UUID("09ec135d-9688-47de-ac71-5b8420b97488")
    client = EnterpriseCoreBusinessReadClient(
        settings(),
        "demo-tenant",
        transport=httpx.MockTransport(
            lambda _: httpx.Response(
                200,
                json={
                    "customerId": str(customer_id),
                    "standing": "GOOD",
                    "maxPaymentTermsDays": 30,
                },
            )
        ),
    )

    result = await client.get_credit(customer_id)

    assert result is not None
    assert result.customer_id == customer_id
    assert result.max_payment_terms_days == 30


@pytest.mark.asyncio
async def test_each_read_asserts_the_owning_logical_agent() -> None:
    seen: list[str] = []

    def respond(request: httpx.Request) -> httpx.Response:
        seen.append(request.headers["X-Agent-Id"])
        if "/crm/" in request.url.path:
            return httpx.Response(
                200,
                json={
                    "id": "09ec135d-9688-47de-ac71-5b8420b97488",
                    "legalName": "Acme Colombia",
                    "active": True,
                },
            )
        if "/inventory/" in request.url.path:
            return httpx.Response(200, json={"sku": "VXT-CHAIR-01", "availableQuantity": 40})
        return httpx.Response(
            200,
            json={
                "customerId": "09ec135d-9688-47de-ac71-5b8420b97488",
                "standing": "GOOD",
                "maxPaymentTermsDays": 30,
            },
        )

    client = EnterpriseCoreBusinessReadClient(
        settings(), "demo-tenant", transport=httpx.MockTransport(respond)
    )
    customer_id = UUID("09ec135d-9688-47de-ac71-5b8420b97488")

    await client.lookup_customer("Acme Colombia")
    await client.get_stock("VXT-CHAIR-01")
    await client.get_credit(customer_id)

    assert seen == ["vextis_crm_agent", "vextis_inventory_agent", "vextis_billing_agent"]


@pytest.mark.asyncio
async def test_authorization_rejection_is_not_reported_as_missing_data() -> None:
    client = EnterpriseCoreBusinessReadClient(
        settings(),
        "demo-tenant",
        transport=httpx.MockTransport(lambda _: httpx.Response(403)),
    )

    with pytest.raises(CoreToolRejectedError, match="403"):
        await client.get_stock("VXT-CHAIR-01")


def test_client_requires_service_token_and_trusted_tenant() -> None:
    with pytest.raises(ValueError, match="AGENT_TOOLS_TOKEN"):
        EnterpriseCoreBusinessReadClient(Settings(), "demo-tenant")
    with pytest.raises(ValueError, match="trusted tenant"):
        EnterpriseCoreBusinessReadClient(settings(), " ")


@pytest.mark.asyncio
async def test_client_rejects_unbounded_lookup_inputs_before_http() -> None:
    async def unexpected_request(_: httpx.Request) -> httpx.Response:
        raise AssertionError("invalid input must not reach Enterprise Core")

    client = EnterpriseCoreBusinessReadClient(
        settings(),
        "demo-tenant",
        transport=httpx.MockTransport(unexpected_request),
    )

    with pytest.raises(ValueError, match="legal_name"):
        await client.lookup_customer(" ")
    with pytest.raises(ValueError, match="sku"):
        await client.get_stock("../../other-tenant")
