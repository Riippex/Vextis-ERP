package com.vextis.workflow.infrastructure.persistence;

import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.ExtractedOrderLine;
import com.vextis.workflow.domain.PlanningDepartment;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.domain.WorkflowPlan;
import com.vextis.workflow.domain.WorkflowPlanStep;
import com.vextis.workflow.domain.ReadinessStatus;
import com.vextis.workflow.domain.WorkflowReadiness;
import com.vextis.workflow.domain.WorkflowReadinessCheck;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPurchaseOrderWorkflowRepository implements PurchaseOrderWorkflowRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcPurchaseOrderWorkflowRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void acquireIdempotencyLock(String tenantId, String operation, String idempotencyKey) {
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                Map.of("lockKey", tenantId + '|' + operation + '|' + idempotencyKey)
        );
    }

    @Override
    public Optional<PurchaseOrderReceipt> findReceipt(
            String tenantId,
            String operation,
            String idempotencyKey
    ) {
        List<ReceiptIds> ids = jdbc.query(
                """
                SELECT
                    (response_body ->> 'purchaseOrderId')::uuid AS purchase_order_id,
                    (response_body ->> 'executionId')::uuid AS execution_id
                FROM idempotency_records
                WHERE tenant_id = :tenantId
                  AND operation = :operation
                  AND idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", idempotencyKey),
                (rs, rowNumber) -> new ReceiptIds(
                        rs.getObject("purchase_order_id", UUID.class),
                        rs.getObject("execution_id", UUID.class)
                )
        );
        if (ids.isEmpty()) {
            return Optional.empty();
        }

        ReceiptIds receiptIds = ids.getFirst();
        PurchaseOrderSource purchaseOrder = findPurchaseOrder(tenantId, receiptIds.purchaseOrderId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record references a missing purchase order"));
        WorkflowExecution execution = findExecution(tenantId, receiptIds.executionId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record references a missing execution"));
        return Optional.of(new PurchaseOrderReceipt(purchaseOrder, execution));
    }

    @Override
    public Optional<WorkflowExecution> findExecution(String tenantId, UUID executionId) {
        List<WorkflowExecution> executions = jdbc.query(
                """
                SELECT id, tenant_id, source_id, goal, state, correlation_id, created_at, updated_at
                FROM workflow_executions
                WHERE tenant_id = :tenantId AND id = :executionId
                """,
                Map.of("tenantId", tenantId, "executionId", executionId),
                (rs, rowNumber) -> mapExecution(
                        rs, findTimeline(executionId), findPlan(executionId).orElse(null),
                        findReadiness(executionId).orElse(null))
        );
        return executions.stream().findFirst();
    }

    @Override
    public Optional<WorkflowExecution> findExecutionResult(
            String tenantId,
            String operation,
            String idempotencyKey
    ) {
        List<UUID> executionIds = jdbc.query(
                """
                SELECT (response_body ->> 'executionId')::uuid AS execution_id
                FROM idempotency_records
                WHERE tenant_id = :tenantId
                  AND operation = :operation
                  AND idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", idempotencyKey),
                (rs, rowNumber) -> rs.getObject("execution_id", UUID.class)
        );
        return executionIds.stream().findFirst().flatMap(id -> findExecution(tenantId, id));
    }

    @Override
    public void saveReceivedPurchaseOrder(
            PurchaseOrderReceipt receipt,
            Actor actor,
            String operation,
            String idempotencyKey
    ) {
        PurchaseOrderSource purchaseOrder = receipt.purchaseOrder();
        WorkflowExecution execution = receipt.execution();

        jdbc.update(
                """
                INSERT INTO workflow_purchase_orders
                    (id, tenant_id, purchase_order_number, customer_name, document_uri, received_at)
                VALUES
                    (:id, :tenantId, :purchaseOrderNumber, :customerName, :documentUri, :receivedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", purchaseOrder.id())
                        .addValue("tenantId", purchaseOrder.tenantId())
                        .addValue("purchaseOrderNumber", purchaseOrder.purchaseOrderNumber())
                        .addValue("customerName", purchaseOrder.customerName())
                        .addValue("documentUri", purchaseOrder.documentUri())
                        .addValue("receivedAt", purchaseOrder.receivedAt())
        );
        jdbc.update(
                """
                INSERT INTO workflow_executions
                    (id, tenant_id, source_type, source_id, goal, state, correlation_id, created_at, updated_at)
                VALUES
                    (:id, :tenantId, 'PURCHASE_ORDER', :sourceId, :goal, :state, :correlationId, :createdAt, :updatedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", execution.id())
                        .addValue("tenantId", execution.tenantId())
                        .addValue("sourceId", execution.sourceId())
                        .addValue("goal", execution.goal())
                        .addValue("state", execution.state().name())
                        .addValue("correlationId", execution.correlationId())
                        .addValue("createdAt", execution.createdAt())
                        .addValue("updatedAt", execution.updatedAt())
        );
        for (ExecutionTimelineEntry entry : execution.timeline()) {
            jdbc.update(
                    """
                    INSERT INTO workflow_timeline_entries
                        (id, execution_id, sequence_number, entry_type, title, detail, occurred_at)
                    VALUES
                        (:id, :executionId, :sequence, :entryType, :title, :detail, :occurredAt)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("executionId", execution.id())
                            .addValue("sequence", entry.sequence())
                            .addValue("entryType", entry.type().name())
                            .addValue("title", entry.title())
                            .addValue("detail", entry.detail())
                            .addValue("occurredAt", entry.occurredAt())
            );
        }

        UUID auditId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action, resource_type, resource_id, result, occurred_at)
                VALUES
                    (:id, :tenantId, :correlationId, :actorType, :actorId, 'RECEIVE_PURCHASE_ORDER',
                     'PURCHASE_ORDER', :resourceId, 'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", auditId)
                        .addValue("tenantId", execution.tenantId())
                        .addValue("correlationId", execution.correlationId())
                        .addValue("actorType", actor.type().name())
                        .addValue("actorId", actor.id())
                        .addValue("resourceId", purchaseOrder.id())
                        .addValue("occurredAt", execution.createdAt())
        );

        String eventId = UUID.randomUUID().toString();
        String eventEnvelope = toJson(Map.of(
                "event_id", eventId,
                "event_type", "purchase_order.received",
                "event_version", 2,
                "occurred_at", execution.createdAt().toString(),
                "producer", "enterprise-core",
                "tenant_id", execution.tenantId(),
                "correlation_id", execution.correlationId(),
                "causation_id", auditId.toString(),
                "actor", Map.of("type", actor.type().name(), "id", actor.id()),
                "payload", Map.of(
                        "purchase_order_id", purchaseOrder.id().toString(),
                        "execution_id", execution.id().toString(),
                        "document_uri", purchaseOrder.documentUri()
                )
        ));
        jdbc.update(
                """
                INSERT INTO outbox_events
                    (event_id, event_type, event_version, aggregate_type, aggregate_id, tenant_id,
                     correlation_id, causation_id, payload, occurred_at)
                VALUES
                    (:eventId, 'purchase_order.received', 2, 'PURCHASE_ORDER', :aggregateId, :tenantId,
                     :correlationId, :causationId, CAST(:payload AS JSONB), :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("aggregateId", purchaseOrder.id().toString())
                        .addValue("tenantId", execution.tenantId())
                        .addValue("correlationId", execution.correlationId())
                        .addValue("causationId", auditId.toString())
                        .addValue("payload", eventEnvelope)
                        .addValue("occurredAt", execution.createdAt())
        );

        jdbc.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, operation, idempotency_key, response_code, response_body)
                VALUES
                    (:id, :tenantId, :operation, :idempotencyKey, 200, CAST(:responseBody AS JSONB))
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", execution.tenantId())
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("responseBody", toJson(Map.of(
                                "purchaseOrderId", purchaseOrder.id().toString(),
                                "executionId", execution.id().toString()
                        )))
        );
    }

    @Override
    public Optional<PurchaseOrderSource> findPurchaseOrder(String tenantId, UUID purchaseOrderId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, purchase_order_number, customer_name, document_uri, received_at
                FROM workflow_purchase_orders
                WHERE tenant_id = :tenantId AND id = :purchaseOrderId
                """,
                Map.of("tenantId", tenantId, "purchaseOrderId", purchaseOrderId),
                (rs, rowNumber) -> new PurchaseOrderSource(
                        rs.getObject("id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getString("purchase_order_number"),
                        rs.getString("customer_name"),
                        rs.getString("document_uri"),
                        rs.getObject("received_at", Instant.class)
                )
        ).stream().findFirst();
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
        int changed = jdbc.update(
                """
                UPDATE workflow_executions
                SET state = :newState, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :executionId AND state = :previousState
                """,
                new MapSqlParameterSource()
                        .addValue("newState", updated.state().name())
                        .addValue("updatedAt", updated.updatedAt())
                        .addValue("tenantId", updated.tenantId())
                        .addValue("executionId", updated.id())
                        .addValue("previousState", previous.state().name())
        );
        if (changed != 1) {
            throw new IllegalStateException("Execution state changed concurrently");
        }

        ExecutionTimelineEntry entry = updated.timeline().getLast();
        jdbc.update(
                """
                INSERT INTO workflow_timeline_entries
                    (id, execution_id, sequence_number, entry_type, title, detail, occurred_at)
                VALUES
                    (:id, :executionId, :sequence, :entryType, :title, :detail, :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("executionId", updated.id())
                        .addValue("sequence", entry.sequence())
                        .addValue("entryType", entry.type().name())
                        .addValue("title", entry.title())
                        .addValue("detail", entry.detail())
                        .addValue("occurredAt", entry.occurredAt())
        );

        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action, resource_type, resource_id, result, occurred_at)
                VALUES
                    (:id, :tenantId, :correlationId, :actorType, :actorId, 'START_EXECUTION_PLANNING',
                     'WORKFLOW_EXECUTION', :resourceId, 'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", updated.tenantId())
                        .addValue("correlationId", updated.correlationId())
                        .addValue("actorType", actor.type().name())
                        .addValue("actorId", actor.id())
                        .addValue("resourceId", updated.id())
                        .addValue("occurredAt", updated.updatedAt())
        );

        jdbc.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, operation, idempotency_key, response_code, response_body)
                VALUES
                    (:id, :tenantId, :operation, :idempotencyKey, 200, CAST(:responseBody AS JSONB))
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", updated.tenantId())
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("responseBody", toJson(Map.of(
                                "executionId", updated.id().toString(),
                                "eventId", eventId.toString()
                        )))
        );
    }

    @Override
    public void savePlanRecorded(
            WorkflowExecution previous,
            WorkflowExecution updated,
            Actor actor,
            String operation,
            String idempotencyKey
    ) {
        int changed = jdbc.update(
                """
                UPDATE workflow_executions
                SET state = :newState, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :executionId AND state = :previousState
                """,
                new MapSqlParameterSource()
                        .addValue("newState", updated.state().name())
                        .addValue("updatedAt", updated.updatedAt())
                        .addValue("tenantId", updated.tenantId())
                        .addValue("executionId", updated.id())
                        .addValue("previousState", previous.state().name())
        );
        if (changed != 1) {
            throw new IllegalStateException("Execution state changed concurrently");
        }

        WorkflowPlan plan = updated.plan();
        if (plan == null) {
            throw new IllegalArgumentException("Recorded execution must contain a plan");
        }
        jdbc.update(
                """
                INSERT INTO workflow_execution_plans
                    (execution_id, summary, model_id, generated_at, requested_payment_terms_days)
                VALUES (:executionId, :summary, :modelId, :generatedAt, :requestedPaymentTermsDays)
                """,
                new MapSqlParameterSource()
                        .addValue("executionId", updated.id())
                        .addValue("summary", plan.summary())
                        .addValue("modelId", plan.modelId())
                        .addValue("generatedAt", plan.generatedAt())
                        .addValue("requestedPaymentTermsDays", plan.requestedPaymentTermsDays())
        );
        for (int index = 0; index < plan.orderLines().size(); index++) {
            ExtractedOrderLine line = plan.orderLines().get(index);
            jdbc.update(
                    """
                    INSERT INTO workflow_execution_order_lines
                        (id, execution_id, sequence_number, sku, quantity)
                    VALUES (:id, :executionId, :sequence, :sku, :quantity)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("executionId", updated.id())
                            .addValue("sequence", index + 1)
                            .addValue("sku", line.sku())
                            .addValue("quantity", line.quantity())
            );
        }
        for (WorkflowPlanStep step : plan.steps()) {
            jdbc.update(
                    """
                    INSERT INTO workflow_execution_plan_steps
                        (id, execution_id, sequence_number, department, objective, requires_approval)
                    VALUES
                        (:id, :executionId, :sequence, :department, :objective, :requiresApproval)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("executionId", updated.id())
                            .addValue("sequence", step.sequence())
                            .addValue("department", step.department().name())
                            .addValue("objective", step.objective())
                            .addValue("requiresApproval", step.requiresApproval())
            );
        }

        ExecutionTimelineEntry entry = updated.timeline().getLast();
        jdbc.update(
                """
                INSERT INTO workflow_timeline_entries
                    (id, execution_id, sequence_number, entry_type, title, detail, occurred_at)
                VALUES
                    (:id, :executionId, :sequence, :entryType, :title, :detail, :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("executionId", updated.id())
                        .addValue("sequence", entry.sequence())
                        .addValue("entryType", entry.type().name())
                        .addValue("title", entry.title())
                        .addValue("detail", entry.detail())
                        .addValue("occurredAt", entry.occurredAt())
        );

        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action, resource_type, resource_id, result, occurred_at)
                VALUES
                    (:id, :tenantId, :correlationId, :actorType, :actorId, 'RECORD_EXECUTION_PLAN',
                     'WORKFLOW_EXECUTION', :resourceId, 'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", updated.tenantId())
                        .addValue("correlationId", updated.correlationId())
                        .addValue("actorType", actor.type().name())
                        .addValue("actorId", actor.id())
                        .addValue("resourceId", updated.id())
                        .addValue("occurredAt", updated.updatedAt())
        );

        jdbc.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, operation, idempotency_key, response_code, response_body)
                VALUES
                    (:id, :tenantId, :operation, :idempotencyKey, 200, CAST(:responseBody AS JSONB))
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", updated.tenantId())
                        .addValue("operation", operation)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("responseBody", toJson(Map.of("executionId", updated.id().toString())))
        );
    }

    @Override
    public void saveReadinessRecorded(
            WorkflowExecution previous,
            WorkflowExecution updated,
            Actor actor,
            String operation,
            String idempotencyKey
    ) {
        WorkflowReadiness readiness = updated.readiness();
        if (readiness == null) {
            throw new IllegalArgumentException("Recorded execution must contain readiness evidence");
        }
        jdbc.update(
                "INSERT INTO workflow_execution_readiness (execution_id, evaluated_at) VALUES (:id, :evaluatedAt)",
                Map.of("id", updated.id(), "evaluatedAt", readiness.evaluatedAt())
        );
        for (WorkflowReadinessCheck check : readiness.checks()) {
            jdbc.update(
                    """
                    INSERT INTO workflow_execution_readiness_checks
                        (id, execution_id, department, status, detail)
                    VALUES (:id, :executionId, :department, :status, :detail)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("executionId", updated.id())
                            .addValue("department", check.department().name())
                            .addValue("status", check.status().name())
                            .addValue("detail", check.detail())
            );
        }
        jdbc.update(
                "UPDATE workflow_executions SET updated_at = :updatedAt WHERE tenant_id = :tenantId AND id = :id",
                Map.of("updatedAt", updated.updatedAt(), "tenantId", updated.tenantId(), "id", updated.id())
        );
        ExecutionTimelineEntry entry = updated.timeline().getLast();
        jdbc.update(
                """
                INSERT INTO workflow_timeline_entries
                    (id, execution_id, sequence_number, entry_type, title, detail, occurred_at)
                VALUES (:id, :executionId, :sequence, :type, :title, :detail, :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID()).addValue("executionId", updated.id())
                        .addValue("sequence", entry.sequence()).addValue("type", entry.type().name())
                        .addValue("title", entry.title()).addValue("detail", entry.detail())
                        .addValue("occurredAt", entry.occurredAt())
        );
        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action,
                     resource_type, resource_id, result, occurred_at)
                VALUES (:id, :tenantId, :correlationId, :actorType, :actorId,
                        'EVALUATE_ORDER_READINESS', 'WORKFLOW_EXECUTION', :resourceId, 'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                        .addValue("tenantId", updated.tenantId()).addValue("correlationId", updated.correlationId())
                        .addValue("actorType", actor.type().name()).addValue("actorId", actor.id())
                        .addValue("resourceId", updated.id()).addValue("occurredAt", updated.updatedAt())
        );
        jdbc.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, operation, idempotency_key, response_code, response_body)
                VALUES (:id, :tenantId, :operation, :idempotencyKey, 200, CAST(:body AS JSONB))
                """,
                new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                        .addValue("tenantId", updated.tenantId()).addValue("operation", operation)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("body", toJson(Map.of("executionId", updated.id().toString())))
        );
    }

    private List<ExecutionTimelineEntry> findTimeline(UUID executionId) {
        return jdbc.query(
                """
                SELECT sequence_number, entry_type, title, detail, occurred_at
                FROM workflow_timeline_entries
                WHERE execution_id = :executionId
                ORDER BY sequence_number
                """,
                Map.of("executionId", executionId),
                (rs, rowNumber) -> new ExecutionTimelineEntry(
                        rs.getInt("sequence_number"),
                        TimelineEntryType.valueOf(rs.getString("entry_type")),
                        rs.getString("title"),
                        rs.getString("detail"),
                        rs.getObject("occurred_at", Instant.class)
                )
        );
    }

    private Optional<WorkflowPlan> findPlan(UUID executionId) {
        List<WorkflowPlan> plans = jdbc.query(
                """
                SELECT summary, model_id, generated_at, requested_payment_terms_days
                FROM workflow_execution_plans
                WHERE execution_id = :executionId
                """,
                Map.of("executionId", executionId),
                (rs, rowNumber) -> new WorkflowPlan(
                        rs.getString("summary"),
                        rs.getString("model_id"),
                        rs.getObject("generated_at", Instant.class),
                        findPlanSteps(executionId),
                        findOrderLines(executionId),
                        rs.getInt("requested_payment_terms_days")
                )
        );
        return plans.stream().findFirst();
    }

    private List<ExtractedOrderLine> findOrderLines(UUID executionId) {
        return jdbc.query(
                """
                SELECT sku, quantity FROM workflow_execution_order_lines
                WHERE execution_id = :executionId ORDER BY sequence_number
                """,
                Map.of("executionId", executionId),
                (rs, row) -> new ExtractedOrderLine(rs.getString("sku"), rs.getInt("quantity"))
        );
    }

    private Optional<WorkflowReadiness> findReadiness(UUID executionId) {
        return jdbc.query(
                "SELECT evaluated_at FROM workflow_execution_readiness WHERE execution_id = :executionId",
                Map.of("executionId", executionId),
                (rs, row) -> new WorkflowReadiness(
                        rs.getObject("evaluated_at", Instant.class), findReadinessChecks(executionId))
        ).stream().findFirst();
    }

    private List<WorkflowReadinessCheck> findReadinessChecks(UUID executionId) {
        return jdbc.query(
                """
                SELECT department, status, detail FROM workflow_execution_readiness_checks
                WHERE execution_id = :executionId ORDER BY department
                """,
                Map.of("executionId", executionId),
                (rs, row) -> new WorkflowReadinessCheck(
                        PlanningDepartment.valueOf(rs.getString("department")),
                        ReadinessStatus.valueOf(rs.getString("status")), rs.getString("detail"))
        );
    }

    private List<WorkflowPlanStep> findPlanSteps(UUID executionId) {
        return jdbc.query(
                """
                SELECT sequence_number, department, objective, requires_approval
                FROM workflow_execution_plan_steps
                WHERE execution_id = :executionId
                ORDER BY sequence_number
                """,
                Map.of("executionId", executionId),
                (rs, rowNumber) -> new WorkflowPlanStep(
                        rs.getInt("sequence_number"),
                        PlanningDepartment.valueOf(rs.getString("department")),
                        rs.getString("objective"),
                        rs.getBoolean("requires_approval")
                )
        );
    }

    private WorkflowExecution mapExecution(
            ResultSet rs,
            List<ExecutionTimelineEntry> timeline,
            WorkflowPlan plan,
            WorkflowReadiness readiness
    ) throws SQLException {
        return new WorkflowExecution(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("source_id", UUID.class),
                rs.getString("goal"),
                ExecutionState.valueOf(rs.getString("state")),
                rs.getString("correlation_id"),
                rs.getObject("created_at", Instant.class),
                rs.getObject("updated_at", Instant.class),
                timeline,
                plan,
                readiness
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize workflow persistence payload", exception);
        }
    }

    private record ReceiptIds(UUID purchaseOrderId, UUID executionId) {
    }
}
