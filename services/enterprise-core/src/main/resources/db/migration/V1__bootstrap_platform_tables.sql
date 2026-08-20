CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    operation VARCHAR(150) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    response_code INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_operation UNIQUE (tenant_id, operation, idempotency_key)
);

CREATE TABLE outbox_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(150) NOT NULL,
    aggregate_id VARCHAR(150) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    causation_id VARCHAR(100),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;
