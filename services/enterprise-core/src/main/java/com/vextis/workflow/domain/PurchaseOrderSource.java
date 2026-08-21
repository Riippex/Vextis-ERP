package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderSource(
        UUID id,
        String tenantId,
        String purchaseOrderNumber,
        String customerName,
        String documentUri,
        Instant receivedAt
) {

    public PurchaseOrderSource {
        if (id == null || receivedAt == null) {
            throw new IllegalArgumentException("Purchase order identity and timestamp are required");
        }
        requireText(tenantId, "Tenant id");
        requireText(purchaseOrderNumber, "Purchase order number");
        requireText(customerName, "Customer name");
        requireText(documentUri, "Document URI");
        if (!documentUri.startsWith("gs://") || documentUri.length() <= 5) {
            throw new IllegalArgumentException("Document URI must point to Google Cloud Storage");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
