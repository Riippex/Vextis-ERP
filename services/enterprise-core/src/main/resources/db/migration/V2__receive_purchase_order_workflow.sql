CREATE TABLE workflow_purchase_orders (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    purchase_order_number VARCHAR(100) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    document_uri VARCHAR(1000) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workflow_purchase_order_number UNIQUE (tenant_id, purchase_order_number),
    CONSTRAINT ck_workflow_purchase_order_document_uri CHECK (document_uri LIKE 'gs://%')
);

CREATE TABLE workflow_executions (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL REFERENCES workflow_purchase_orders(id),
    goal VARCHAR(500) NOT NULL,
    state VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workflow_execution_correlation UNIQUE (tenant_id, correlation_id),
    CONSTRAINT ck_workflow_execution_state CHECK (
        state IN ('RECEIVED', 'PLANNING', 'RUNNING', 'WAITING_APPROVAL', 'COMPLETED', 'FAILED')
    )
);

CREATE TABLE workflow_timeline_entries (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workflow_timeline_sequence UNIQUE (execution_id, sequence_number),
    CONSTRAINT ck_workflow_timeline_sequence CHECK (sequence_number > 0)
);

CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id VARCHAR(150) NOT NULL,
    action VARCHAR(150) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID NOT NULL,
    result VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_workflow_executions_tenant_state ON workflow_executions (tenant_id, state);
CREATE INDEX ix_workflow_timeline_execution ON workflow_timeline_entries (execution_id, sequence_number);
CREATE INDEX ix_audit_records_correlation ON audit_records (tenant_id, correlation_id, occurred_at);
