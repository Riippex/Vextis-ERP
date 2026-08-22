import json

import httpx
import pytest
from pydantic import SecretStr

from tests.unit.event_factory import purchase_order_event
from vextis_agents.app.config import Settings
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
    EnterpriseCorePlanningClient,
)
from vextis_agents.workflows.order_to_cash.events import PurchaseOrderReceivedV2
from vextis_agents.workflows.order_to_cash.planning import GeneratedPlan


@pytest.mark.asyncio
async def test_client_propagates_trusted_context_to_narrow_tool() -> None:
    captured: httpx.Request | None = None

    def respond(request: httpx.Request) -> httpx.Response:
        nonlocal captured
        captured = request
        return httpx.Response(
            200,
            json={
                "id": "8d3f290d-1322-44a2-8bd7-3b325f170e07",
                "state": "PLANNING",
                "correlationId": "corr-001",
                "updatedAt": "2026-08-21T03:30:02Z",
                "goal": "Process purchase order",
                "purchaseOrderNumber": "PO-2026-001",
                "customerName": "Acme Colombia",
                "documentUri": "gs://vextis-demo/orders/po-2026-001.pdf",
            },
        )

    client = EnterpriseCorePlanningClient(settings(), httpx.MockTransport(respond))
    event = PurchaseOrderReceivedV2.model_validate(purchase_order_event())
    result = await client.start_planning(event)

    assert result.state == "PLANNING"
    assert captured is not None
    assert captured.headers["X-Tenant-Id"] == "demo-tenant"
    assert captured.headers["X-Agent-Id"] == "coordinator-agent"
    assert captured.headers["Idempotency-Key"] == "8b962f0a-1850-4fcc-a6f5-97e45c67a16e"
    assert json.loads(captured.content)["documentUri"].startswith("gs://")


@pytest.mark.asyncio
async def test_client_records_validated_plan_with_stable_idempotency_key() -> None:
    captured: httpx.Request | None = None

    def respond(request: httpx.Request) -> httpx.Response:
        nonlocal captured
        if request.url.path.endswith("/planning"):
            return httpx.Response(
                200,
                json={
                    "id": "8d3f290d-1322-44a2-8bd7-3b325f170e07",
                    "state": "PLANNING",
                    "correlationId": "corr-001",
                    "updatedAt": "2026-08-21T03:30:02Z",
                    "goal": "Process purchase order",
                    "purchaseOrderNumber": "PO-2026-001",
                    "customerName": "Acme Colombia",
                    "documentUri": "gs://vextis-demo/orders/po-2026-001.pdf",
                },
            )
        captured = request
        return httpx.Response(
            200,
            json={
                "id": "8d3f290d-1322-44a2-8bd7-3b325f170e07",
                "state": "RUNNING",
                "correlationId": "corr-001",
                "updatedAt": "2026-08-21T03:30:04Z",
            },
        )

    client = EnterpriseCorePlanningClient(settings(), httpx.MockTransport(respond))
    event = PurchaseOrderReceivedV2.model_validate(purchase_order_event())
    context = await client.start_planning(event)
    plan = GeneratedPlan.model_validate(
        {
            "summary": "Validate the purchase order.",
            "steps": [
                {
                    "sequence": 1,
                    "department": "CRM_SALES",
                    "objective": "Validate customer context.",
                    "requires_approval": False,
                }
            ],
        }
    )

    result = await client.record_plan(event, context, plan, "gemini-3.5-flash")

    assert result.state == "RUNNING"
    assert captured is not None
    assert captured.headers["Idempotency-Key"].endswith(":record-plan")
    body = json.loads(captured.content)
    assert body["modelId"] == "gemini-3.5-flash"
    assert body["steps"][0]["requiresApproval"] is False


@pytest.mark.asyncio
async def test_client_does_not_retry_deterministic_rejection() -> None:
    transport = httpx.MockTransport(lambda request: httpx.Response(409))
    client = EnterpriseCorePlanningClient(settings(), transport)

    with pytest.raises(CoreToolRejectedError):
        await client.start_planning(PurchaseOrderReceivedV2.model_validate(purchase_order_event()))


@pytest.mark.asyncio
async def test_client_surfaces_transient_core_failure() -> None:
    transport = httpx.MockTransport(lambda request: httpx.Response(503))
    client = EnterpriseCorePlanningClient(settings(), transport)

    with pytest.raises(CoreToolUnavailableError):
        await client.start_planning(PurchaseOrderReceivedV2.model_validate(purchase_order_event()))


def settings() -> Settings:
    return Settings(
        enterprise_core_url="http://enterprise-core.test",
        agent_tools_token=SecretStr("test-service-token"),
        coordinator_agent_id="coordinator-agent",
    )
