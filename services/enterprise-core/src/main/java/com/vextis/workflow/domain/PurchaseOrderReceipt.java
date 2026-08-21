package com.vextis.workflow.domain;

public record PurchaseOrderReceipt(PurchaseOrderSource purchaseOrder, WorkflowExecution execution) {

    public PurchaseOrderReceipt {
        if (purchaseOrder == null || execution == null) {
            throw new IllegalArgumentException("Purchase order and execution are required");
        }
        if (!purchaseOrder.id().equals(execution.sourceId())) {
            throw new IllegalArgumentException("Execution must reference the received purchase order");
        }
        if (!purchaseOrder.tenantId().equals(execution.tenantId())) {
            throw new IllegalArgumentException("Purchase order and execution must belong to the same tenant");
        }
    }
}
