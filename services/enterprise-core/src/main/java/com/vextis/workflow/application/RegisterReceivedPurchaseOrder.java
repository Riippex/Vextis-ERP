package com.vextis.workflow.application;

import com.vextis.workflow.domain.PurchaseOrderReceipt;

public interface RegisterReceivedPurchaseOrder {
    PurchaseOrderReceipt register(ReceivePurchaseOrderCommand command);
}
