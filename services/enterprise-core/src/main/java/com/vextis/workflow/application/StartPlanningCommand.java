package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;

import java.util.UUID;

public record StartPlanningCommand(
        String tenantId,
        Actor actor,
        UUID executionId,
        UUID eventId,
        String correlationId,
        String documentUri,
        String idempotencyKey
) {

    public StartPlanningCommand {
        if (actor == null || executionId == null || eventId == null) {
            throw new IllegalArgumentException("Actor, execution id and event id are required");
        }
        if (tenantId == null || tenantId.isBlank() || correlationId == null || correlationId.isBlank()
                || documentUri == null || documentUri.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Tenant, correlation, document URI and idempotency key are required");
        }
    }
}
