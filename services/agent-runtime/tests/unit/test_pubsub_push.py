from fastapi.testclient import TestClient

from tests.unit.event_factory import pubsub_push_body
from vextis_agents.app.config import Settings
from vextis_agents.app.main import create_app
from vextis_agents.tools.core_api.planning import (
    CoreToolUnavailableError,
    PlanningResult,
)
from vextis_agents.workflows.order_to_cash.events import PurchaseOrderReceivedV2


class PlanningToolStub:
    def __init__(self, unavailable: bool = False) -> None:
        self.event: PurchaseOrderReceivedV2 | None = None
        self.unavailable = unavailable

    async def start_planning(self, event: PurchaseOrderReceivedV2) -> PlanningResult:
        self.event = event
        if self.unavailable:
            raise CoreToolUnavailableError("temporary")
        return PlanningResult(
            id=str(event.payload.execution_id),
            state="PLANNING",
            correlationId=event.correlation_id,
            updatedAt="2026-08-21T03:30:02Z",
        )


def test_push_invokes_typed_planning_tool_and_acknowledges() -> None:
    tool = PlanningToolStub()
    app = create_app(Settings(pubsub_push_enabled=True), planning_tool=tool)

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert tool.event is not None
    assert tool.event.tenant_id == "demo-tenant"


def test_malformed_event_is_acknowledged_without_calling_tool() -> None:
    tool = PlanningToolStub()
    app = create_app(Settings(pubsub_push_enabled=True), planning_tool=tool)

    response = TestClient(app).post(
        "/events/pubsub",
        content=b'{"message":{"data":"not-base64"}}',
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert tool.event is None


def test_transient_core_failure_requests_pubsub_retry() -> None:
    tool = PlanningToolStub(unavailable=True)
    app = create_app(Settings(pubsub_push_enabled=True), planning_tool=tool)

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 503
