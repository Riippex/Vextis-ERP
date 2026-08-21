package com.vextis.workflow.application;

import com.vextis.workflow.domain.PurchaseOrderReceipt;

public interface ReceivePurchaseOrderUseCase {

    PurchaseOrderReceipt receive(ReceivePurchaseOrderCommand command);
}
