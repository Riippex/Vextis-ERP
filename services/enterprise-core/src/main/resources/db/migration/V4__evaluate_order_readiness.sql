CREATE TABLE workflow_execution_order_lines (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES workflow_execution_plans(execution_id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT uq_workflow_order_line_sequence UNIQUE (execution_id, sequence_number),
    CONSTRAINT ck_workflow_order_line_quantity CHECK (quantity BETWEEN 1 AND 1000000)
);

ALTER TABLE workflow_execution_plans
    ADD COLUMN requested_payment_terms_days INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_workflow_payment_terms CHECK (requested_payment_terms_days BETWEEN 0 AND 365);

CREATE TABLE crm_customers (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT uq_crm_customer_name UNIQUE (tenant_id, legal_name)
);

CREATE TABLE inventory_stock (
    tenant_id VARCHAR(100) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    available_quantity INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, sku),
    CONSTRAINT ck_inventory_available_quantity CHECK (available_quantity >= 0)
);

CREATE TABLE billing_credit_profiles (
    tenant_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    standing VARCHAR(20) NOT NULL,
    max_payment_terms_days INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, customer_id),
    CONSTRAINT ck_billing_credit_standing CHECK (standing IN ('GOOD', 'REVIEW', 'BLOCKED')),
    CONSTRAINT ck_billing_payment_terms CHECK (max_payment_terms_days BETWEEN 0 AND 365)
);

CREATE TABLE workflow_execution_readiness (
    execution_id UUID PRIMARY KEY REFERENCES workflow_executions(id) ON DELETE CASCADE,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workflow_execution_readiness_checks (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES workflow_execution_readiness(execution_id) ON DELETE CASCADE,
    department VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    CONSTRAINT uq_workflow_readiness_department UNIQUE (execution_id, department),
    CONSTRAINT ck_workflow_readiness_department CHECK (
        department IN ('CRM_SALES', 'INVENTORY_OPERATIONS', 'FINANCE_BILLING')
    ),
    CONSTRAINT ck_workflow_readiness_status CHECK (status IN ('READY', 'REVIEW_REQUIRED'))
);
