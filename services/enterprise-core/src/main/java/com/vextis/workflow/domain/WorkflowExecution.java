package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowExecution(
        UUID id,
        String tenantId,
        UUID sourceId,
        String goal,
        ExecutionState state,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        List<ExecutionTimelineEntry> timeline
) {

    public WorkflowExecution {
        if (id == null || sourceId == null || state == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Execution identity, state and timestamps are required");
        }
        if (tenantId == null || tenantId.isBlank() || goal == null || goal.isBlank()
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Execution tenant, goal and correlation id are required");
        }
        timeline = List.copyOf(timeline);
    }
}
