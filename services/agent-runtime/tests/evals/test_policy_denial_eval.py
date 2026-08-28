import httpx
import pytest
from pydantic import SecretStr

from tests.unit.event_factory import approval_decided_event
from vextis_agents.app.config import Settings
from vextis_agents.tools.core_api.business_reads import EnterpriseCoreBusinessReadClient
from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    EnterpriseCorePlanningClient,
)
from vextis_agents.workflows.order_to_cash.events import WorkflowApprovalDecidedV1


@pytest.mark.asyncio
async def test_business_read_denial_on_403_raises_rejected_error() -> None:
    def handler_403(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"error": "Agent not allowed for tool"})

    transport = httpx.MockTransport(handler_403)
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("test-token"),
    )
    client = EnterpriseCoreBusinessReadClient(
        settings=settings,
        tenant_id="foreign-tenant",
        transport=transport,
    )

    with pytest.raises(CoreToolRejectedError) as exc_info:
        await client.lookup_customer("Acme")

    err = str(exc_info.value).lower()
    assert "forbidden" in err or "not authorized" in err or "403" in err or "rejected" in err


def test_planning_client_denial_on_missing_token_raises_value_error() -> None:
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=None,
    )
    with pytest.raises(ValueError, match="VEXTIS_AGENT_TOOLS_TOKEN is required"):
        EnterpriseCorePlanningClient(settings=settings)


@pytest.mark.asyncio
async def test_planning_client_rejects_unauthorized_billing_invoice_attempt() -> None:
    def handler_unauthorized(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"error": "Invalid service credential"})

    transport = httpx.MockTransport(handler_unauthorized)
    settings = Settings(
        enterprise_core_url="https://core.vextis.local",
        agent_tools_token=SecretStr("bad-token"),
    )
    client = EnterpriseCorePlanningClient(
        settings=settings,
        transport=transport,
    )

    event = WorkflowApprovalDecidedV1.model_validate(approval_decided_event())

    with pytest.raises(CoreToolRejectedError):
        await client.issue_invoice(event)
