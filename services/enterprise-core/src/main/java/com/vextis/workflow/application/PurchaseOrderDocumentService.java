package com.vextis.workflow.application;

import com.vextis.workflow.application.port.PurchaseOrderDocumentStorage;
import com.vextis.workflow.domain.PurchaseOrderUpload;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderDocumentService implements PreparePurchaseOrderUploadUseCase, ReceivePurchaseOrderUseCase {
    private final PurchaseOrderDocumentStorage storage;
    private final RegisterReceivedPurchaseOrder registrar;

    public PurchaseOrderDocumentService(
            PurchaseOrderDocumentStorage storage,
            RegisterReceivedPurchaseOrder registrar
    ) {
        this.storage = storage;
        this.registrar = registrar;
    }

    @Override
    public PurchaseOrderUpload prepare(PreparePurchaseOrderUploadCommand command) {
        return storage.prepareUpload(
                command.tenantId(), command.fileName().trim(), command.contentType().trim(), command.sizeBytes());
    }

    @Override
    public com.vextis.workflow.domain.PurchaseOrderReceipt receive(ReceivePurchaseOrderCommand command) {
        storage.assertReady(command.tenantId(), command.documentUri().trim());
        return registrar.register(command);
    }
}
