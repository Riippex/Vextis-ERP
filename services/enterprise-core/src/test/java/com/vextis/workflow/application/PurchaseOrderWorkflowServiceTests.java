package com.vextis.workflow.application;

import com.vextis.billing.CreditLookup;
import com.vextis.crm.CustomerLookup;
import com.vextis.inventory.StockLookup;
import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import com.vextis.workflow.ExecutionOverview;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ApprovalDecision;
import com.vextis.workflow.domain.ApprovalStatus;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExtractedOrderLine;
import com.vextis.workflow.domain.PlanningDepartment;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.domain.WorkflowPlanStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderWorkflowServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-21T03:30:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final PurchaseOrderWorkflowService service = new PurchaseOrderWorkflowService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            (tenant, name) -> Optional.of(new CustomerLookup.CustomerSnapshot(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"), name, true)),
            (tenant, sku) -> Optional.of(new StockLookup.StockSnapshot(sku, 40)),
            (tenant, customerId) -> Optional.of(new CreditLookup.CreditSnapshot(
                    CreditLookup.CreditStanding.GOOD, 30))
    );

    @Test
    void receivesPurchaseOrderWithAuditReadyExecution() {
        PurchaseOrderReceipt receipt = service.receive(command("receive-po-001"));

        assertThat(receipt.purchaseOrder().purchaseOrderNumber()).isEqualTo("PO-2026-001");
        assertThat(receipt.purchaseOrder().receivedAt()).isEqualTo(NOW);
        assertThat(receipt.execution().state()).isEqualTo(ExecutionState.RECEIVED);
        assertThat(receipt.execution().correlationId()).isNotBlank();
        assertThat(receipt.execution().timeline()).singleElement().satisfies(entry -> {
            assertThat(entry.sequence()).isEqualTo(1);
            assertThat(entry.title()).isEqualTo("Orden recibida");
        });
        assertThat(repository.savedActor).isEqualTo(new Actor(Actor.Type.USER, "demo-user"));
        assertThat(repository.savedIdempotencyKey).isEqualTo("receive-po-001");
    }

    @Test
    void returnsSameReceiptWhenIdempotencyKeyIsRepeated() {
        PurchaseOrderReceipt first = service.receive(command("same-key"));
        PurchaseOrderReceipt second = service.receive(command("same-key"));

        assertThat(second).isEqualTo(first);
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(repository.lockCount).isEqualTo(2);
    }

    @Test
    void rejectsReusingIdempotencyKeyForDifferentInput() {
        service.receive(command("same-key"));

        ReceivePurchaseOrderCommand conflicting = new ReceivePurchaseOrderCommand(
                "demo-tenant",
                new Actor(Actor.Type.USER, "demo-user"),
                "PO-2026-999",
                "Another customer",
                "gs://vextis-demo/orders/another.pdf",
                "same-key"
        );

        assertThatThrownBy(() -> service.receive(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key was already used for a different purchase order");
        assertThat(repository.saveCount).isEqualTo(1);
    }

    @Test
    void startsPlanningFromTrustedEventContextAndIsIdempotent() {
        PurchaseOrderReceipt received = service.receive(command("receive-po-001"));
        UUID eventId = UUID.fromString("8b962f0a-1850-4fcc-a6f5-97e45c67a16e");
        StartPlanningCommand planning = new StartPlanningCommand(
                "demo-tenant",
                new Actor(Actor.Type.AGENT, "coordinator-agent"),
                received.execution().id(),
                eventId,
                received.execution().correlationId(),
                received.purchaseOrder().documentUri(),
                eventId.toString()
        );

        PlanningContext first = service.startPlanning(planning);
        PlanningContext repeated = service.startPlanning(planning);

        assertThat(first.execution().state()).isEqualTo(ExecutionState.PLANNING);
        assertThat(first.execution().timeline()).hasSize(2);
        assertThat(first.execution().timeline().getLast().title()).isEqualTo("Agent planning started");
        assertThat(first.purchaseOrder()).isEqualTo(received.purchaseOrder());
        assertThat(repeated).isEqualTo(first);
        assertThat(repository.planningSaveCount).isEqualTo(1);
    }

    @Test
    void recordsStructuredPlanAndMovesExecutionToRunningIdempotently() {
        PurchaseOrderReceipt received = service.receive(command("receive-po-001"));
        UUID eventId = UUID.fromString("8b962f0a-1850-4fcc-a6f5-97e45c67a16e");
        PlanningContext planning = service.startPlanning(new StartPlanningCommand(
                "demo-tenant",
                new Actor(Actor.Type.AGENT, "coordinator-agent"),
                received.execution().id(),
                eventId,
                received.execution().correlationId(),
                received.purchaseOrder().documentUri(),
                eventId.toString()
        ));
        RecordPlanCommand command = new RecordPlanCommand(
                "demo-tenant",
                new Actor(Actor.Type.AGENT, "coordinator-agent"),
                planning.execution().id(),
                planning.execution().correlationId(),
                "gemini-3.5-flash",
                "Validate the customer, inventory, and commercial terms.",
                List.of(
                        new WorkflowPlanStep(1, PlanningDepartment.CRM_SALES, "Validate customer context.", false),
                        new WorkflowPlanStep(2, PlanningDepartment.INVENTORY_OPERATIONS, "Check availability.", false),
                        new WorkflowPlanStep(3, PlanningDepartment.FINANCE_BILLING, "Validate terms.", true)
                ),
                List.of(new ExtractedOrderLine("VXT-CHAIR-01", 10)),
                30,
                eventId + ":record-plan"
        );

        WorkflowExecution first = service.recordPlan(command);
        WorkflowExecution repeated = service.recordPlan(command);

        assertThat(first.state()).isEqualTo(ExecutionState.RUNNING);
        assertThat(first.plan()).isNotNull();
        assertThat(first.plan().modelId()).isEqualTo("gemini-3.5-flash");
        assertThat(first.plan().steps()).hasSize(3);
        assertThat(first.timeline().getLast().title()).isEqualTo("Structured plan recorded");
        assertThat(repeated).isEqualTo(first);
        assertThat(repository.planSaveCount).isEqualTo(1);
    }

    @Test
    void evaluatesAuthoritativeReadinessWithoutChangingBusinessState() {
        PurchaseOrderReceipt received = service.receive(command("receive-po-001"));
        UUID eventId = UUID.fromString("8b962f0a-1850-4fcc-a6f5-97e45c67a16e");
        PlanningContext planning = service.startPlanning(new StartPlanningCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), received.execution().id(),
                eventId, received.execution().correlationId(), received.purchaseOrder().documentUri(), eventId.toString()));
        service.recordPlan(new RecordPlanCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), planning.execution().id(),
                planning.execution().correlationId(), "gemini-3.5-flash", "Validate readiness.",
                List.of(new WorkflowPlanStep(1, PlanningDepartment.CRM_SALES, "Validate.", false)),
                List.of(new ExtractedOrderLine("VXT-CHAIR-01", 10)), 30, eventId + ":record-plan"));

        EvaluateReadinessCommand command = new EvaluateReadinessCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), received.execution().id(),
                received.execution().correlationId(), eventId + ":evaluate-readiness");
        WorkflowExecution evaluated = service.evaluateReadiness(command);
        WorkflowExecution repeated = service.evaluateReadiness(command);

        assertThat(evaluated.state()).isEqualTo(ExecutionState.RUNNING);
        assertThat(evaluated.readiness().checks()).allMatch(check -> check.status().name().equals("READY"));
        assertThat(evaluated.timeline().getLast().title()).isEqualTo("Read-only readiness evaluated");
        assertThat(repeated).isEqualTo(evaluated);
        assertThat(repository.readinessSaveCount).isEqualTo(1);
    }

    @Test
    void requestsAndDecidesHumanApprovalWithDurableIdentity() {
        PurchaseOrderReceipt received = service.receive(command("receive-po-001"));
        UUID eventId = UUID.fromString("8b962f0a-1850-4fcc-a6f5-97e45c67a16e");
        PlanningContext planning = service.startPlanning(new StartPlanningCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), received.execution().id(),
                eventId, received.execution().correlationId(), received.purchaseOrder().documentUri(), eventId.toString()));
        service.recordPlan(new RecordPlanCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), planning.execution().id(),
                planning.execution().correlationId(), "gemini-3.5-flash", "Validate readiness.",
                List.of(new WorkflowPlanStep(1, PlanningDepartment.FINANCE_BILLING, "Validate.", true)),
                List.of(new ExtractedOrderLine("VXT-CHAIR-01", 10)), 30, eventId + ":record-plan"));
        service.evaluateReadiness(new EvaluateReadinessCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), received.execution().id(),
                received.execution().correlationId(), eventId + ":evaluate-readiness"));

        WorkflowExecution waiting = service.requestApproval(new RequestApprovalCommand(
                "demo-tenant", new Actor(Actor.Type.AGENT, "coordinator-agent"), received.execution().id(),
                received.execution().correlationId(), "Proceed after human review.", eventId + ":request-approval"));
        WorkflowExecution approved = service.decideApproval(new DecideApprovalCommand(
                "demo-tenant", new Actor(Actor.Type.USER, "firebase-user"), received.execution().id(),
                waiting.approval().id(), ApprovalDecision.APPROVE, "Evidence reviewed",
                "decide-approval-0001"));

        assertThat(waiting.state()).isEqualTo(ExecutionState.WAITING_APPROVAL);
        assertThat(approved.approval().status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.approval().decidedBy()).isEqualTo("firebase-user");
        assertThat(repository.approvalRequestSaveCount).isEqualTo(1);
        assertThat(repository.approvalDecisionSaveCount).isEqualTo(1);
    }

    @Test
    void rejectsPlanningWhenCorrelationDoesNotMatch() {
        PurchaseOrderReceipt received = service.receive(command("receive-po-001"));

        StartPlanningCommand planning = new StartPlanningCommand(
                "demo-tenant",
                new Actor(Actor.Type.AGENT, "coordinator-agent"),
                received.execution().id(),
                UUID.randomUUID(),
                "wrong-correlation",
                received.purchaseOrder().documentUri(),
                "planning-key-0001"
        );

        assertThatThrownBy(() -> service.startPlanning(planning))
                .isInstanceOf(WorkflowConflictException.class)
                .hasMessage("Correlation id does not match the execution");
        assertThat(repository.planningSaveCount).isZero();
    }

    private ReceivePurchaseOrderCommand command(String idempotencyKey) {
        return new ReceivePurchaseOrderCommand(
                "demo-tenant",
                new Actor(Actor.Type.USER, "demo-user"),
                " PO-2026-001 ",
                " Acme Colombia ",
                " gs://vextis-demo/orders/po-2026-001.pdf ",
                idempotencyKey
        );
    }

    private static final class InMemoryRepository implements PurchaseOrderWorkflowRepository {

        private PurchaseOrderReceipt receipt;
        private Actor savedActor;
        private String savedIdempotencyKey;
        private int saveCount;
        private int lockCount;
        private String planningIdempotencyKey;
        private int planningSaveCount;
        private String planIdempotencyKey;
        private int planSaveCount;
        private String readinessIdempotencyKey;
        private int readinessSaveCount;
        private String approvalRequestIdempotencyKey;
        private int approvalRequestSaveCount;
        private String approvalDecisionIdempotencyKey;
        private int approvalDecisionSaveCount;

        @Override
        public void acquireIdempotencyLock(String tenantId, String operation, String idempotencyKey) {
            lockCount++;
        }

        @Override
        public Optional<PurchaseOrderReceipt> findReceipt(
                String tenantId,
                String operation,
                String idempotencyKey
        ) {
            if (idempotencyKey.equals(savedIdempotencyKey)) {
                return Optional.ofNullable(receipt);
            }
            return Optional.empty();
        }

        @Override
        public Optional<WorkflowExecution> findExecution(String tenantId, UUID executionId) {
            return Optional.ofNullable(receipt)
                    .map(PurchaseOrderReceipt::execution)
                    .filter(execution -> execution.id().equals(executionId));
        }

        @Override
        public List<ExecutionOverview.ExecutionSummary> findRecentExecutions(String tenantId, int limit) {
            return Optional.ofNullable(receipt)
                    .map(value -> List.of(new ExecutionOverview.ExecutionSummary(
                            value.execution().id(),
                            value.purchaseOrder().purchaseOrderNumber(),
                            value.purchaseOrder().customerName(),
                            value.execution().state().name(),
                            value.execution().correlationId(),
                            value.execution().updatedAt())))
                    .orElseGet(List::of);
        }

        @Override
        public Optional<PurchaseOrderSource> findPurchaseOrder(String tenantId, UUID purchaseOrderId) {
            return Optional.ofNullable(receipt)
                    .map(PurchaseOrderReceipt::purchaseOrder)
                    .filter(source -> source.id().equals(purchaseOrderId));
        }

        @Override
        public Optional<WorkflowExecution> findExecutionResult(
                String tenantId,
                String operation,
                String idempotencyKey
        ) {
            if (idempotencyKey.equals(planningIdempotencyKey)) {
                return Optional.ofNullable(receipt).map(PurchaseOrderReceipt::execution);
            }
            if (idempotencyKey.equals(planIdempotencyKey)) {
                return Optional.ofNullable(receipt).map(PurchaseOrderReceipt::execution);
            }
            if (idempotencyKey.equals(readinessIdempotencyKey)) {
                return Optional.ofNullable(receipt).map(PurchaseOrderReceipt::execution);
            }
            if (idempotencyKey.equals(approvalRequestIdempotencyKey)
                    || idempotencyKey.equals(approvalDecisionIdempotencyKey)) {
                return Optional.ofNullable(receipt).map(PurchaseOrderReceipt::execution);
            }
            return Optional.empty();
        }

        @Override
        public void saveReceivedPurchaseOrder(
                PurchaseOrderReceipt receipt,
                Actor actor,
                String operation,
                String idempotencyKey
        ) {
            this.receipt = receipt;
            this.savedActor = actor;
            this.savedIdempotencyKey = idempotencyKey;
            saveCount++;
        }

        @Override
        public void savePlanningStarted(
                WorkflowExecution previous,
                WorkflowExecution updated,
                Actor actor,
                UUID eventId,
                String operation,
                String idempotencyKey
        ) {
            receipt = new PurchaseOrderReceipt(receipt.purchaseOrder(), updated);
            planningIdempotencyKey = idempotencyKey;
            planningSaveCount++;
        }

        @Override
        public void savePlanRecorded(
                WorkflowExecution previous,
                WorkflowExecution updated,
                Actor actor,
                String operation,
                String idempotencyKey
        ) {
            receipt = new PurchaseOrderReceipt(receipt.purchaseOrder(), updated);
            planIdempotencyKey = idempotencyKey;
            planSaveCount++;
        }

        @Override
        public void saveReadinessRecorded(
                WorkflowExecution previous, WorkflowExecution updated, Actor actor,
                String operation, String idempotencyKey
        ) {
            receipt = new PurchaseOrderReceipt(receipt.purchaseOrder(), updated);
            readinessIdempotencyKey = idempotencyKey;
            readinessSaveCount++;
        }

        @Override
        public void saveApprovalRequested(
                WorkflowExecution previous, WorkflowExecution updated, Actor actor,
                String operation, String idempotencyKey
        ) {
            receipt = new PurchaseOrderReceipt(receipt.purchaseOrder(), updated);
            approvalRequestIdempotencyKey = idempotencyKey;
            approvalRequestSaveCount++;
        }

        @Override
        public void saveApprovalDecided(
                WorkflowExecution previous, WorkflowExecution updated, Actor actor,
                String operation, String idempotencyKey
        ) {
            receipt = new PurchaseOrderReceipt(receipt.purchaseOrder(), updated);
            approvalDecisionIdempotencyKey = idempotencyKey;
            approvalDecisionSaveCount++;
        }
    }
}
