package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;

import java.util.UUID;

public record ReserveApprovedStockCommand(
        String tenantId, Actor actor, UUID orderId, String sku, int quantity,
        String correlationId, String idempotencyKey
) {
}
