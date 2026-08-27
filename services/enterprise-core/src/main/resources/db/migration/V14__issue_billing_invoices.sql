ALTER TABLE workflow_execution_plans
    ADD COLUMN currency CHAR(3);

ALTER TABLE workflow_execution_order_lines
    ADD COLUMN unit_price NUMERIC(19, 2),
    ADD CONSTRAINT ck_workflow_order_line_unit_price CHECK (unit_price IS NULL OR unit_price > 0);

CREATE TABLE billing_invoices (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    tax NUMERIC(19, 2) NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_terms_days INTEGER NOT NULL,
    actor_id VARCHAR(150) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_billing_invoice_order UNIQUE (tenant_id, order_id),
    CONSTRAINT uq_billing_invoice_execution UNIQUE (tenant_id, execution_id),
    CONSTRAINT uq_billing_invoice_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_billing_invoice_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_billing_invoice_amounts CHECK (subtotal > 0 AND tax >= 0 AND total = subtotal + tax),
    CONSTRAINT ck_billing_invoice_status CHECK (status = 'ISSUED'),
    CONSTRAINT ck_billing_invoice_payment_terms CHECK (payment_terms_days BETWEEN 0 AND 365)
);

CREATE TABLE billing_invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES billing_invoices(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    line_subtotal NUMERIC(19, 2) NOT NULL,
    CONSTRAINT uq_billing_invoice_line_sequence UNIQUE (invoice_id, sequence_number),
    CONSTRAINT ck_billing_invoice_line_quantity CHECK (quantity BETWEEN 1 AND 1000000),
    CONSTRAINT ck_billing_invoice_line_amounts CHECK (unit_price > 0 AND line_subtotal = unit_price * quantity)
);

CREATE INDEX ix_billing_invoices_tenant_issued
    ON billing_invoices (tenant_id, issued_at DESC);

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['get_credit', 'create_invoice']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_billing_agent'
  AND status = 'ACTIVE';
