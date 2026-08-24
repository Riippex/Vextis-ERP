package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;

import java.util.UUID;

public record EvaluateReadinessCommand(
        String tenantId,
        Actor actor,
        UUID executionId,
        String correlationId,
        String idempotencyKey
) {
    public EvaluateReadinessCommand {
        if (tenantId == null || tenantId.isBlank() || actor == null || executionId == null
                || correlationId == null || correlationId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Readiness command context is required");
        }
    }
}
