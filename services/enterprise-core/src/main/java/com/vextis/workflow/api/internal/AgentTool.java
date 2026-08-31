package com.vextis.workflow.api.internal;

enum AgentTool {
    LOOKUP_CUSTOMER("lookup_customer"),
    LIST_CUSTOMERS("list_customers"),
    SEARCH_CUSTOMER_ORDERS("search_customer_orders"),
    GET_STOCK("get_stock"),
    SEARCH_INVENTORY("search_inventory"),
    GET_CREDIT("get_credit"),
    START_EXECUTION_PLANNING("start_execution_planning"),
    RECORD_EXECUTION_PLAN("record_execution_plan"),
    EVALUATE_ORDER_READINESS("evaluate_order_readiness"),
    REQUEST_WORKFLOW_APPROVAL("request_workflow_approval"),
    RESERVE_STOCK("reserve_stock"),
    CREATE_INVOICE("create_invoice"),
    REGISTER_QUOTE_ASSET("register_quote_asset"),
    SEARCH_KNOWLEDGE_BASE("search_knowledge_base"),
    INGEST_KNOWLEDGE_DOCUMENT("ingest_knowledge_document");

    private final String policyName;

    AgentTool(String policyName) {
        this.policyName = policyName;
    }

    String policyName() {
        return policyName;
    }
}
