package com.vextis.workflow.application;

import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.domain.WorkflowPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PurchaseOrderWorkflowService implements ReceivePurchaseOrderUseCase, FindExecutionUseCase,
        StartPlanningUseCase, RecordPlanUseCase {

    static final String RECEIVE_OPERATION = "workflow.receive-purchase-order";
    static final String START_PLANNING_OPERATION = "workflow.start-planning";
    static final String RECORD_PLAN_OPERATION = "workflow.record-plan";

    private final PurchaseOrderWorkflowRepository repository;
    private final Clock clock;

    public PurchaseOrderWorkflowService(PurchaseOrderWorkflowRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PurchaseOrderReceipt receive(ReceivePurchaseOrderCommand command) {
        repository.acquireIdempotencyLock(command.tenantId(), RECEIVE_OPERATION, command.idempotencyKey());

        Optional<PurchaseOrderReceipt> existing = repository.findReceipt(
                command.tenantId(),
                RECEIVE_OPERATION,
                command.idempotencyKey()
        );
        if (existing.isPresent()) {
            PurchaseOrderReceipt existingReceipt = existing.get();
            assertSameRequest(existingReceipt, command);
            return existingReceipt;
        }

        Instant now = clock.instant();
        UUID purchaseOrderId = UUID.randomUUID();
        PurchaseOrderSource purchaseOrder = new PurchaseOrderSource(
                purchaseOrderId,
                command.tenantId(),
                command.purchaseOrderNumber().trim(),
                command.customerName().trim(),
                command.documentUri().trim(),
                now
        );
        WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                command.tenantId(),
                purchaseOrderId,
                "Procesar la orden " + purchaseOrder.purchaseOrderNumber() + " de " + purchaseOrder.customerName(),
                ExecutionState.RECEIVED,
                UUID.randomUUID().toString(),
                now,
                now,
                List.of(new ExecutionTimelineEntry(
                        1,
                        TimelineEntryType.RECEIVED,
                        "Orden recibida",
                        "El documento quedó registrado y listo para planificación agentiva.",
                        now
                ))
        );
        PurchaseOrderReceipt receipt = new PurchaseOrderReceipt(purchaseOrder, execution);
        repository.saveReceivedPurchaseOrder(receipt, command.actor(), RECEIVE_OPERATION, command.idempotencyKey());
        return receipt;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowExecution> findById(String tenantId, UUID executionId) {
        return repository.findExecution(tenantId, executionId);
    }

    @Override
    @Transactional
    public PlanningContext startPlanning(StartPlanningCommand command) {
        if (command.actor().type() != Actor.Type.AGENT) {
            throw new WorkflowConflictException("Only an authenticated agent can start planning");
        }
        repository.acquireIdempotencyLock(
                command.tenantId(),
                START_PLANNING_OPERATION,
                command.idempotencyKey()
        );
        Optional<WorkflowExecution> previousResult = repository.findExecutionResult(
                command.tenantId(),
                START_PLANNING_OPERATION,
                command.idempotencyKey()
        );
        if (previousResult.isPresent()) {
            WorkflowExecution previous = previousResult.get();
            PurchaseOrderSource previousSource = repository.findPurchaseOrder(command.tenantId(), previous.sourceId())
                    .orElseThrow(() -> new WorkflowNotFoundException("Purchase order source was not found"));
            return new PlanningContext(previous, previousSource);
        }

        WorkflowExecution current = repository.findExecution(command.tenantId(), command.executionId())
                .orElseThrow(() -> new WorkflowNotFoundException("Execution was not found for tenant"));
        if (!current.correlationId().equals(command.correlationId())) {
            throw new WorkflowConflictException("Correlation id does not match the execution");
        }
        PurchaseOrderSource source = repository.findPurchaseOrder(command.tenantId(), current.sourceId())
                .orElseThrow(() -> new WorkflowNotFoundException("Purchase order source was not found"));
        if (!source.documentUri().equals(command.documentUri())) {
            throw new WorkflowConflictException("Document URI does not match the execution source");
        }

        WorkflowExecution updated;
        try {
            updated = current.startPlanning(clock.instant());
        } catch (IllegalStateException exception) {
            throw new WorkflowConflictException(exception.getMessage());
        }
        repository.savePlanningStarted(
                current,
                updated,
                command.actor(),
                command.eventId(),
                START_PLANNING_OPERATION,
                command.idempotencyKey()
        );
        return new PlanningContext(updated, source);
    }

    @Override
    @Transactional
    public WorkflowExecution recordPlan(RecordPlanCommand command) {
        if (command.actor().type() != Actor.Type.AGENT) {
            throw new WorkflowConflictException("Only an authenticated agent can record a plan");
        }
        repository.acquireIdempotencyLock(command.tenantId(), RECORD_PLAN_OPERATION, command.idempotencyKey());
        Optional<WorkflowExecution> previousResult = repository.findExecutionResult(
                command.tenantId(),
                RECORD_PLAN_OPERATION,
                command.idempotencyKey()
        );
        if (previousResult.isPresent()) {
            return previousResult.get();
        }

        WorkflowExecution current = repository.findExecution(command.tenantId(), command.executionId())
                .orElseThrow(() -> new WorkflowNotFoundException("Execution was not found for tenant"));
        if (!current.correlationId().equals(command.correlationId())) {
            throw new WorkflowConflictException("Correlation id does not match the execution");
        }

        Instant now = clock.instant();
        WorkflowPlan plan;
        WorkflowExecution updated;
        try {
            plan = new WorkflowPlan(command.summary(), command.modelId(), now, command.steps());
            updated = current.recordPlan(plan, now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new WorkflowConflictException(exception.getMessage());
        }
        repository.savePlanRecorded(
                current,
                updated,
                command.actor(),
                RECORD_PLAN_OPERATION,
                command.idempotencyKey()
        );
        return updated;
    }

    private void assertSameRequest(PurchaseOrderReceipt existing, ReceivePurchaseOrderCommand command) {
        PurchaseOrderSource purchaseOrder = existing.purchaseOrder();
        boolean sameRequest = purchaseOrder.purchaseOrderNumber().equals(command.purchaseOrderNumber().trim())
                && purchaseOrder.customerName().equals(command.customerName().trim())
                && purchaseOrder.documentUri().equals(command.documentUri().trim());
        if (!sameRequest) {
            throw new IllegalArgumentException("Idempotency key was already used for a different purchase order");
        }
    }
}
