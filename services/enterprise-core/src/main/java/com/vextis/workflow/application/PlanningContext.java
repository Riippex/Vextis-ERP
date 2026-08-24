package com.vextis.workflow.application;

import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.WorkflowExecution;

public record PlanningContext(
        WorkflowExecution execution,
        PurchaseOrderSource purchaseOrder
) {

    public PlanningContext {
        if (execution == null || purchaseOrder == null) {
            throw new IllegalArgumentException("Execution and purchase order are required");
        }
        if (!execution.sourceId().equals(purchaseOrder.id())) {
            throw new IllegalArgumentException("Planning context source does not match execution");
        }
    }
}
