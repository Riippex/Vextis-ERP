package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExtractedOrderLine;
import com.vextis.workflow.domain.WorkflowPlanStep;

import java.util.List;
import java.util.UUID;

public record RecordPlanCommand(
        String tenantId,
        Actor actor,
        UUID executionId,
        String correlationId,
        String modelId,
        String summary,
        List<WorkflowPlanStep> steps,
        List<ExtractedOrderLine> orderLines,
        int requestedPaymentTermsDays,
        String currency,
        String idempotencyKey
) {

    public RecordPlanCommand(
            String tenantId, Actor actor, UUID executionId, String correlationId, String modelId,
            String summary, List<WorkflowPlanStep> steps, List<ExtractedOrderLine> orderLines,
            int requestedPaymentTermsDays, String idempotencyKey
    ) {
        this(tenantId, actor, executionId, correlationId, modelId, summary, steps, orderLines,
                requestedPaymentTermsDays, null, idempotencyKey);
    }

    public RecordPlanCommand {
        if (actor == null || executionId == null || steps == null || orderLines == null) {
            throw new IllegalArgumentException("Actor, execution id, plan steps and order lines are required");
        }
        if (tenantId == null || tenantId.isBlank() || correlationId == null || correlationId.isBlank()
                || modelId == null || modelId.isBlank() || summary == null || summary.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Tenant, correlation, model, summary and idempotency key are required");
        }
        steps = List.copyOf(steps);
        orderLines = List.copyOf(orderLines);
        currency = currency == null ? null : currency.trim().toUpperCase();
    }
}
