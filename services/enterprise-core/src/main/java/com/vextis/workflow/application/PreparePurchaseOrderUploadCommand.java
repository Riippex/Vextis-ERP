package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;

public record PreparePurchaseOrderUploadCommand(
        String tenantId,
        Actor actor,
        String fileName,
        String contentType,
        int sizeBytes
) {
    public PreparePurchaseOrderUploadCommand {
        if (actor == null) {
            throw new IllegalArgumentException("Actor is required");
        }
        requireText(tenantId, "Tenant id");
        requireText(fileName, "File name");
        requireText(contentType, "Content type");
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("Document must not be empty");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
