package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.List;

public record WorkflowReadiness(Instant evaluatedAt, List<WorkflowReadinessCheck> checks) {
    public WorkflowReadiness {
        if (evaluatedAt == null || checks == null || checks.size() != 3) {
            throw new IllegalArgumentException("Readiness requires exactly three department checks");
        }
        checks = List.copyOf(checks);
        if (checks.stream().map(WorkflowReadinessCheck::department).distinct().count() != 3) {
            throw new IllegalArgumentException("Readiness departments must be unique");
        }
    }
}
