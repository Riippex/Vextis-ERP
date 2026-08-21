package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;

public record ReceivePurchaseOrderCommand(
        String tenantId,
        Actor actor,
        String purchaseOrderNumber,
        String customerName,
        String documentUri,
        String idempotencyKey
) {

    public ReceivePurchaseOrderCommand {
        if (actor == null) {
            throw new IllegalArgumentException("Actor is required");
        }
        requireText(tenantId, "Tenant id");
        requireText(purchaseOrderNumber, "Purchase order number");
        requireText(customerName, "Customer name");
        requireText(documentUri, "Document URI");
        requireText(idempotencyKey, "Idempotency key");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
