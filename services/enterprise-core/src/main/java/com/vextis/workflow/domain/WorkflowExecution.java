package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.ArrayList;
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

    public WorkflowExecution startPlanning(Instant now) {
        if (state != ExecutionState.RECEIVED) {
            throw new IllegalStateException("Only a received execution can start planning");
        }
        ArrayList<ExecutionTimelineEntry> updatedTimeline = new ArrayList<>(timeline);
        updatedTimeline.add(new ExecutionTimelineEntry(
                timeline.size() + 1,
                TimelineEntryType.STATUS_CHANGED,
                "Agent planning started",
                "Agent Runtime accepted the event and started planning the order.",
                now
        ));
        return new WorkflowExecution(
                id,
                tenantId,
                sourceId,
                goal,
                ExecutionState.PLANNING,
                correlationId,
                createdAt,
                now,
                updatedTimeline
        );
    }
}
