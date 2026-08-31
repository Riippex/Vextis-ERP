package com.vextis.workflow.application.port;

import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.ExecutionOverview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderWorkflowRepository {

    void acquireIdempotencyLock(String tenantId, String operation, String idempotencyKey);

    Optional<PurchaseOrderReceipt> findReceipt(String tenantId, String operation, String idempotencyKey);

    Optional<WorkflowExecution> findExecution(String tenantId, UUID executionId);

    Optional<WorkflowExecution> findExecutionBySourceId(String tenantId, UUID sourceId);

    List<ExecutionOverview.ExecutionSummary> findRecentExecutions(String tenantId, int limit);

    List<ExecutionOverview.DepartmentVolume> findExecutionVolumeByDepartment(String tenantId);

    List<ExecutionOverview.WeeklyVolume> findCompletedExecutionVolumeByWeek(String tenantId, int weeks);

    Optional<PurchaseOrderSource> findPurchaseOrder(String tenantId, UUID purchaseOrderId);

    Optional<WorkflowExecution> findExecutionResult(
            String tenantId,
            String operation,
            String idempotencyKey
    );

    void saveReceivedPurchaseOrder(
            PurchaseOrderReceipt receipt,
            Actor actor,
            String operation,
            String idempotencyKey
    );

    void savePlanningStarted(
            WorkflowExecution previous,
            WorkflowExecution updated,
            Actor actor,
            UUID eventId,
            String operation,
            String idempotencyKey
    );

    void savePlanRecorded(
            WorkflowExecution previous,
            WorkflowExecution updated,
            Actor actor,
            String operation,
            String idempotencyKey
    );

    void saveReadinessRecorded(
            WorkflowExecution previous,
            WorkflowExecution updated,
            Actor actor,
            String operation,
            String idempotencyKey
    );

    void saveApprovalRequested(
            WorkflowExecution previous, WorkflowExecution updated, Actor actor,
            String operation, String idempotencyKey
    );

    void saveApprovalDecided(
            WorkflowExecution previous, WorkflowExecution updated, Actor actor,
            String operation, String idempotencyKey
    );

    void saveCompleted(
            WorkflowExecution previous, WorkflowExecution updated, Actor actor,
            String operation, String idempotencyKey
    );

    void saveInvoiceIssued(WorkflowExecution previous, WorkflowExecution updated);
}
