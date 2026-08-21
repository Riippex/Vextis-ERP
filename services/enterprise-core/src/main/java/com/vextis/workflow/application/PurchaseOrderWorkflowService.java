package com.vextis.workflow.application;

import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PurchaseOrderWorkflowService implements ReceivePurchaseOrderUseCase, FindExecutionUseCase {

    static final String RECEIVE_OPERATION = "workflow.receive-purchase-order";

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
