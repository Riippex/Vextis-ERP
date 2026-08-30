package com.vextis.workflow.domain;

/** Raised when a tenant attempts to receive an already registered purchase-order number. */
public final class DuplicatePurchaseOrderException extends RuntimeException {

    public DuplicatePurchaseOrderException(String purchaseOrderNumber) {
        super("Purchase order " + purchaseOrderNumber + " has already been received.");
    }
}
