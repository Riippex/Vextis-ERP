package com.vextis.workflow.api.internal;

enum AgentTool {
    LOOKUP_CUSTOMER("lookup_customer"),
    GET_STOCK("get_stock"),
    GET_CREDIT("get_credit"),
    START_EXECUTION_PLANNING("start_execution_planning"),
    RECORD_EXECUTION_PLAN("record_execution_plan"),
    EVALUATE_ORDER_READINESS("evaluate_order_readiness"),
    REQUEST_WORKFLOW_APPROVAL("request_workflow_approval"),
    RESERVE_STOCK("reserve_stock"),
    CREATE_INVOICE("create_invoice");

    private final String policyName;

    AgentTool(String policyName) {
        this.policyName = policyName;
    }

    String policyName() {
        return policyName;
    }
}
