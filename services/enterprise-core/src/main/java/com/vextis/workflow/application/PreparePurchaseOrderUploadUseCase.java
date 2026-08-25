package com.vextis.workflow.application;

import com.vextis.workflow.domain.PurchaseOrderUpload;

public interface PreparePurchaseOrderUploadUseCase {
    PurchaseOrderUpload prepare(PreparePurchaseOrderUploadCommand command);
}
