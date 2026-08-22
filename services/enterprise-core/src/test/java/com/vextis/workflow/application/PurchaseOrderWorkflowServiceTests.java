package com.vextis.workflow.application;

import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderWorkflowServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-21T03:30:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final PurchaseOrderWorkflowService service = new PurchaseOrderWorkflowService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
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

        WorkflowExecution first = service.startPlanning(planning);
        WorkflowExecution repeated = service.startPlanning(planning);

        assertThat(first.state()).isEqualTo(ExecutionState.PLANNING);
        assertThat(first.timeline()).hasSize(2);
        assertThat(first.timeline().getLast().title()).isEqualTo("Agent planning started");
        assertThat(repeated).isEqualTo(first);
        assertThat(repository.planningSaveCount).isEqualTo(1);
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
    }
}
