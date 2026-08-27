package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;

import java.util.UUID;

public record IssueApprovedInvoiceCommand(
        String tenantId,
        Actor actor,
        UUID orderId,
        UUID executionId,
        String correlationId,
        String idempotencyKey
) {
    public IssueApprovedInvoiceCommand {
        if (actor == null || orderId == null || executionId == null) {
            throw new IllegalArgumentException("Actor, order and execution are required");
        }
        if (tenantId == null || tenantId.isBlank() || correlationId == null || correlationId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Tenant, correlation and idempotency key are required");
        }
    }
}
