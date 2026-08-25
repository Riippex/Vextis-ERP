package com.vextis.workflow.application.port;

import com.vextis.workflow.domain.PurchaseOrderUpload;

public interface PurchaseOrderDocumentStorage {
    PurchaseOrderUpload prepareUpload(String tenantId, String fileName, String contentType, int sizeBytes);

    void assertReady(String tenantId, String documentUri);
}
