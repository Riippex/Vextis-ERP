import logging

import pytest
from fastapi.testclient import TestClient

from tests.unit.event_factory import approval_decided_event, pubsub_push_body
from vextis_agents.app.config import Settings
from vextis_agents.app.main import create_app
from vextis_agents.tools.core_api.planning import (
    CoreToolUnavailableError,
    InvoiceLineResult,
    InvoiceResult,
    PlanningResult,
    ReservationResult,
)
from vextis_agents.workflows.order_to_cash.events import (
    PurchaseOrderReceivedV2,
    WorkflowApprovalDecidedV1,
)
from vextis_agents.workflows.order_to_cash.planning import (
    GeneratedPlan,
    PlanGenerationUnavailableError,
    PlanningContext,
)


class PlanningToolStub:
    def __init__(
        self,
        unavailable: bool = False,
        state: str = "PLANNING",
        readiness_evaluated: bool = False,
        approval_status: str | None = None,
    ) -> None:
        self.event: PurchaseOrderReceivedV2 | None = None
        self.plan: GeneratedPlan | None = None
        self.unavailable = unavailable
        self.state = state
        self.readiness_evaluated = readiness_evaluated
        self.approval_status = approval_status
        self.readiness_calls = 0
        self.approval_calls = 0
        self.reservations: list[tuple[str, int]] = []
        self.invoice_calls = 0

    async def start_planning(self, event: PurchaseOrderReceivedV2) -> PlanningContext:
        self.event = event
        if self.unavailable:
            raise CoreToolUnavailableError("temporary")
        return PlanningContext(
            id=str(event.payload.execution_id),
            state=self.state,
            correlationId=event.correlation_id,
            updatedAt="2026-08-21T03:30:02Z",
            goal="Process purchase order",
            purchaseOrderNumber="PO-2026-001",
            customerName="Acme Colombia",
            documentUri=event.payload.document_uri,
            readinessEvaluated=self.readiness_evaluated,
            approvalStatus=self.approval_status,
        )

    async def evaluate_readiness(
        self, event: PurchaseOrderReceivedV2, context: PlanningContext
    ) -> PlanningResult:
        self.readiness_calls += 1
        return PlanningResult(
            id=context.id,
            state="RUNNING",
            correlationId=context.correlation_id,
            updatedAt="2026-08-21T03:30:06Z",
        )

    async def record_plan(
        self,
        event: PurchaseOrderReceivedV2,
        context: PlanningContext,
        plan: GeneratedPlan,
        model_id: str,
    ) -> PlanningResult:
        self.plan = plan
        return PlanningResult(
            id=context.id,
            state="RUNNING",
            correlationId=context.correlation_id,
            updatedAt="2026-08-21T03:30:04Z",
        )

    async def request_approval(
        self, event: PurchaseOrderReceivedV2, context: PlanningContext, recommendation: str
    ) -> PlanningResult:
        self.approval_calls += 1
        return PlanningResult(
            id=context.id,
            state="WAITING_APPROVAL",
            correlationId=context.correlation_id,
            updatedAt="2026-08-21T03:30:08Z",
        )

    async def reserve_stock(
        self, event: WorkflowApprovalDecidedV1, sku: str, quantity: int
    ) -> ReservationResult:
        self.reservations.append((sku, quantity))
        return ReservationResult(
            id="f47c82aa-9739-4b55-9c7f-0950a9218e1d",
            orderId=str(event.payload.order_id),
            sku=sku,
            quantity=quantity,
            status="RESERVED",
            createdAt="2026-08-24T20:00:01Z",
        )

    async def issue_invoice(self, event: WorkflowApprovalDecidedV1) -> InvoiceResult:
        self.invoice_calls += 1
        return InvoiceResult(
            id="3e2fb128-12e8-48fa-acdd-4748e00657ef",
            orderId=str(event.payload.order_id),
            executionId=str(event.payload.execution_id),
            customerName="Acme Colombia",
            subtotal="1000.00",
            tax="190.00",
            total="1190.00",
            currency="COP",
            status="ISSUED",
            paymentTermsDays=30,
            issuedAt="2026-08-27T18:00:00Z",
            correlationId=event.correlation_id,
            lines=[
                InvoiceLineResult(
                    sku="VXT-CHAIR-01",
                    quantity=10,
                    unitPrice="100.00",
                    lineSubtotal="1000.00",
                )
            ],
        )


class PlanGeneratorStub:
    def __init__(self, unavailable: bool = False) -> None:
        self.calls = 0
        self.unavailable = unavailable

    @property
    def model_id(self) -> str:
        return "gemini-3.5-flash"

    async def generate(self, context: PlanningContext) -> GeneratedPlan:
        self.calls += 1
        if self.unavailable:
            raise PlanGenerationUnavailableError("temporary")
        return GeneratedPlan.model_validate(
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
                "order_lines": [{"sku": "VXT-CHAIR-01", "quantity": 10}],
                "requested_payment_terms_days": 30,
            }
        )


def test_push_invokes_typed_planning_tool_and_acknowledges() -> None:
    tool = PlanningToolStub()
    generator = PlanGeneratorStub()
    app = create_app(
        Settings(pubsub_push_enabled=True),
        planning_tool=tool,
        plan_generator=generator,
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert tool.event is not None
    assert tool.event.tenant_id == "demo-tenant"
    assert tool.plan is not None
    assert generator.calls == 1
    assert tool.readiness_calls == 1
    assert tool.approval_calls == 1


def test_malformed_event_is_acknowledged_without_calling_tool() -> None:
    tool = PlanningToolStub()
    app = create_app(
        Settings(pubsub_push_enabled=True),
        planning_tool=tool,
        plan_generator=PlanGeneratorStub(),
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=b'{"message":{"data":"not-base64"}}',
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert tool.event is None


def test_transient_core_failure_requests_pubsub_retry(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.WARNING)
    tool = PlanningToolStub(unavailable=True)
    app = create_app(
        Settings(pubsub_push_enabled=True),
        planning_tool=tool,
        plan_generator=PlanGeneratorStub(),
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 503
    assert "dependency=enterprise_core reason=temporary" in caplog.text
    assert "gs://" not in caplog.text


def test_transient_gemini_failure_requests_pubsub_retry(caplog: pytest.LogCaptureFixture) -> None:
    caplog.set_level(logging.WARNING)
    tool = PlanningToolStub()
    generator = PlanGeneratorStub(unavailable=True)
    app = create_app(
        Settings(pubsub_push_enabled=True),
        planning_tool=tool,
        plan_generator=generator,
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 503
    assert generator.calls == 1
    assert tool.plan is None
    assert "dependency=gemini reason=temporary" in caplog.text
    assert "gs://" not in caplog.text


def test_replay_after_readiness_resumes_approval_without_duplicate_gemini_call() -> None:
    tool = PlanningToolStub(state="RUNNING", readiness_evaluated=True)
    generator = PlanGeneratorStub()
    app = create_app(
        Settings(pubsub_push_enabled=True),
        planning_tool=tool,
        plan_generator=generator,
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert generator.calls == 0
    assert tool.plan is None
    assert tool.readiness_calls == 0
    assert tool.approval_calls == 1


def test_replay_resumes_readiness_without_duplicate_gemini_call() -> None:
    tool = PlanningToolStub(state="RUNNING", readiness_evaluated=False)
    generator = PlanGeneratorStub()
    app = create_app(
        Settings(pubsub_push_enabled=True), planning_tool=tool, plan_generator=generator
    )

    response = TestClient(app).post(
        "/events/pubsub", content=pubsub_push_body(), headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 204
    assert generator.calls == 0
    assert tool.readiness_calls == 1
    assert tool.approval_calls == 1


def test_replayed_approval_request_skips_all_duplicate_work() -> None:
    tool = PlanningToolStub(
        state="WAITING_APPROVAL", readiness_evaluated=True, approval_status="PENDING"
    )
    generator = PlanGeneratorStub()
    app = create_app(
        Settings(pubsub_push_enabled=True), planning_tool=tool, plan_generator=generator
    )

    response = TestClient(app).post(
        "/events/pubsub", content=pubsub_push_body(), headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 204
    assert generator.calls == 0
    assert tool.readiness_calls == 0
    assert tool.approval_calls == 0


def test_approved_workflow_reserves_each_exact_order_line() -> None:
    tool = PlanningToolStub()
    app = create_app(
        Settings(pubsub_push_enabled=True), planning_tool=tool, plan_generator=PlanGeneratorStub()
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(approval_decided_event()),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert tool.reservations == [("VXT-CHAIR-01", 10)]
    assert tool.invoice_calls == 1


def test_rejected_workflow_never_reserves_stock() -> None:
    tool = PlanningToolStub()
    app = create_app(
        Settings(pubsub_push_enabled=True), planning_tool=tool, plan_generator=PlanGeneratorStub()
    )

    response = TestClient(app).post(
        "/events/pubsub",
        content=pubsub_push_body(approval_decided_event("REJECTED")),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 204
    assert tool.reservations == []
    assert tool.invoice_calls == 0
