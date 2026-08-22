package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;
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
        String idempotencyKey
) {

    public RecordPlanCommand {
        if (actor == null || executionId == null || steps == null) {
            throw new IllegalArgumentException("Actor, execution id and plan steps are required");
        }
        if (tenantId == null || tenantId.isBlank() || correlationId == null || correlationId.isBlank()
                || modelId == null || modelId.isBlank() || summary == null || summary.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Tenant, correlation, model, summary and idempotency key are required");
        }
        steps = List.copyOf(steps);
    }
}
