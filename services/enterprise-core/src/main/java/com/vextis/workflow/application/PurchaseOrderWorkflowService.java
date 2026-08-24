package com.vextis.workflow.application;

import com.vextis.billing.CreditLookup;
import com.vextis.crm.CustomerLookup;
import com.vextis.inventory.StockLookup;
import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import com.vextis.workflow.ExecutionOverview;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.domain.WorkflowPlan;
import com.vextis.workflow.domain.WorkflowReadiness;
import com.vextis.workflow.domain.WorkflowReadinessCheck;
import com.vextis.workflow.domain.ReadinessStatus;
import com.vextis.workflow.domain.PlanningDepartment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PurchaseOrderWorkflowService implements ReceivePurchaseOrderUseCase, FindExecutionUseCase,
        StartPlanningUseCase, RecordPlanUseCase, EvaluateReadinessUseCase,
        RequestApprovalUseCase, DecideApprovalUseCase, ExecutionOverview {

    static final String RECEIVE_OPERATION = "workflow.receive-purchase-order";
    static final String START_PLANNING_OPERATION = "workflow.start-planning";
    static final String RECORD_PLAN_OPERATION = "workflow.record-plan";
    static final String EVALUATE_READINESS_OPERATION = "workflow.evaluate-readiness";
    static final String REQUEST_APPROVAL_OPERATION = "workflow.request-approval";
    static final String DECIDE_APPROVAL_OPERATION = "workflow.decide-approval";

    private final PurchaseOrderWorkflowRepository repository;
    private final Clock clock;
    private final CustomerLookup customers;
    private final StockLookup stock;
    private final CreditLookup credit;

    public PurchaseOrderWorkflowService(
            PurchaseOrderWorkflowRepository repository,
            Clock clock,
            CustomerLookup customers,
            StockLookup stock,
            CreditLookup credit
    ) {
        this.repository = repository;
        this.clock = clock;
        this.customers = customers;
        this.stock = stock;
        this.credit = credit;
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
    @Transactional(readOnly = true)
    public List<ExecutionOverview.ExecutionSummary> findRecent(String tenantId, int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Execution summary limit must be between 1 and 50");
        }
        return repository.findRecentExecutions(tenantId, limit);
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
            plan = new WorkflowPlan(
                    command.summary(), command.modelId(), now, command.steps(),
                    command.orderLines(), command.requestedPaymentTermsDays());
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

    @Override
    @Transactional
    public WorkflowExecution evaluateReadiness(EvaluateReadinessCommand command) {
        if (command.actor().type() != Actor.Type.AGENT) {
            throw new WorkflowConflictException("Only an authenticated agent can evaluate readiness");
        }
        repository.acquireIdempotencyLock(command.tenantId(), EVALUATE_READINESS_OPERATION, command.idempotencyKey());
        Optional<WorkflowExecution> previousResult = repository.findExecutionResult(
                command.tenantId(), EVALUATE_READINESS_OPERATION, command.idempotencyKey());
        if (previousResult.isPresent()) {
            return previousResult.get();
        }
        WorkflowExecution current = repository.findExecution(command.tenantId(), command.executionId())
                .orElseThrow(() -> new WorkflowNotFoundException("Execution was not found for tenant"));
        if (!current.correlationId().equals(command.correlationId()) || current.plan() == null) {
            throw new WorkflowConflictException("Execution context or structured plan is invalid");
        }

        PurchaseOrderSource source = repository.findPurchaseOrder(command.tenantId(), current.sourceId())
                .orElseThrow(() -> new WorkflowNotFoundException("Purchase order source was not found"));
        CustomerLookup.CustomerSnapshot customer = customers
                .findByLegalName(command.tenantId(), source.customerName()).orElse(null);

        WorkflowReadinessCheck crm = customer != null && customer.active()
                ? check(PlanningDepartment.CRM_SALES, ReadinessStatus.READY,
                        "Active customer matched: " + customer.legalName() + '.')
                : check(PlanningDepartment.CRM_SALES, ReadinessStatus.REVIEW_REQUIRED,
                        "No active customer matched the purchase order name.");

        List<String> shortages = current.plan().orderLines().stream()
                .filter(line -> stock.findBySku(command.tenantId(), line.sku())
                        .map(snapshot -> snapshot.availableQuantity() < line.quantity()).orElse(true))
                .map(line -> line.sku() + " requires " + line.quantity())
                .toList();
        WorkflowReadinessCheck inventory = shortages.isEmpty()
                ? check(PlanningDepartment.INVENTORY_OPERATIONS, ReadinessStatus.READY,
                        "All " + current.plan().orderLines().size() + " extracted SKU lines have sufficient stock.")
                : check(PlanningDepartment.INVENTORY_OPERATIONS, ReadinessStatus.REVIEW_REQUIRED,
                        "Missing or insufficient stock: " + String.join(", ", shortages));

        WorkflowReadinessCheck billing = billingCheck(command, current, customer);
        Instant now = clock.instant();
        WorkflowExecution updated;
        try {
            updated = current.recordReadiness(new WorkflowReadiness(now, List.of(crm, inventory, billing)), now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new WorkflowConflictException(exception.getMessage());
        }
        repository.saveReadinessRecorded(
                current, updated, command.actor(), EVALUATE_READINESS_OPERATION, command.idempotencyKey());
        return updated;
    }

    @Override
    @Transactional
    public WorkflowExecution requestApproval(RequestApprovalCommand command) {
        if (command.actor().type() != Actor.Type.AGENT) {
            throw new WorkflowConflictException("Only an authenticated agent can request approval");
        }
        repository.acquireIdempotencyLock(command.tenantId(), REQUEST_APPROVAL_OPERATION, command.idempotencyKey());
        Optional<WorkflowExecution> previousResult = repository.findExecutionResult(
                command.tenantId(), REQUEST_APPROVAL_OPERATION, command.idempotencyKey());
        if (previousResult.isPresent()) {
            return previousResult.get();
        }
        WorkflowExecution current = repository.findExecution(command.tenantId(), command.executionId())
                .orElseThrow(() -> new WorkflowNotFoundException("Execution was not found for tenant"));
        if (!current.correlationId().equals(command.correlationId())) {
            throw new WorkflowConflictException("Correlation id does not match the execution");
        }
        Instant now = clock.instant();
        WorkflowExecution updated;
        try {
            updated = current.requestApproval(
                    command.recommendation(), command.actor().id(), now, now.plusSeconds(86_400));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new WorkflowConflictException(exception.getMessage());
        }
        repository.saveApprovalRequested(
                current, updated, command.actor(), REQUEST_APPROVAL_OPERATION, command.idempotencyKey());
        return updated;
    }

    @Override
    @Transactional
    public WorkflowExecution decideApproval(DecideApprovalCommand command) {
        if (command.actor().type() != Actor.Type.USER) {
            throw new WorkflowConflictException("Only an authenticated user can decide approval");
        }
        repository.acquireIdempotencyLock(command.tenantId(), DECIDE_APPROVAL_OPERATION, command.idempotencyKey());
        Optional<WorkflowExecution> previousResult = repository.findExecutionResult(
                command.tenantId(), DECIDE_APPROVAL_OPERATION, command.idempotencyKey());
        if (previousResult.isPresent()) {
            return previousResult.get();
        }
        WorkflowExecution current = repository.findExecution(command.tenantId(), command.executionId())
                .orElseThrow(() -> new WorkflowNotFoundException("Execution was not found for tenant"));
        WorkflowExecution updated;
        try {
            updated = current.decideApproval(
                    command.approvalId(), command.decision(), command.actor().id(), command.reason(), clock.instant());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new WorkflowConflictException(exception.getMessage());
        }
        repository.saveApprovalDecided(
                current, updated, command.actor(), DECIDE_APPROVAL_OPERATION, command.idempotencyKey());
        return updated;
    }

    private WorkflowReadinessCheck billingCheck(
            EvaluateReadinessCommand command,
            WorkflowExecution execution,
            CustomerLookup.CustomerSnapshot customer
    ) {
        if (customer == null) {
            return check(PlanningDepartment.FINANCE_BILLING, ReadinessStatus.REVIEW_REQUIRED,
                    "Credit terms cannot be checked until the customer is matched.");
        }
        return credit.findByCustomer(command.tenantId(), customer.id())
                .filter(profile -> profile.standing() == CreditLookup.CreditStanding.GOOD
                        && execution.plan().requestedPaymentTermsDays() <= profile.maxPaymentTermsDays())
                .map(profile -> check(PlanningDepartment.FINANCE_BILLING, ReadinessStatus.READY,
                        "Credit standing is good and requested terms fit the "
                                + profile.maxPaymentTermsDays() + "-day limit."))
                .orElseGet(() -> check(PlanningDepartment.FINANCE_BILLING, ReadinessStatus.REVIEW_REQUIRED,
                        "Credit standing or requested payment terms require review."));
    }

    private WorkflowReadinessCheck check(
            PlanningDepartment department, ReadinessStatus status, String detail
    ) {
        return new WorkflowReadinessCheck(department, status, detail);
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
