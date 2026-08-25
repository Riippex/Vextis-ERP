CREATE TABLE live_sessions (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    conversation_id UUID NOT NULL,
    actor_id VARCHAR(150) NOT NULL,
    state VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    CONSTRAINT ck_live_sessions_state CHECK (state IN ('CREATED', 'ACTIVE', 'CLOSED'))
);

CREATE INDEX ix_live_sessions_tenant ON live_sessions (tenant_id, id);
