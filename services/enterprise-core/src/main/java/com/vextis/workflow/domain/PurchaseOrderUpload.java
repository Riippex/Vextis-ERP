package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.List;

public record PurchaseOrderUpload(
        String uploadUrl,
        String documentUri,
        Instant expiresAt,
        List<FormField> formFields
) {
    public PurchaseOrderUpload {
        formFields = List.copyOf(formFields);
    }

    public record FormField(String name, String value) {
    }
}
