CREATE TABLE IF NOT EXISTS proposal_assets (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    quote_id VARCHAR(100) NOT NULL,
    storage_uri VARCHAR(1000) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    model_id VARCHAR(150) NOT NULL,
    prompt_summary VARCHAR(500) NOT NULL,
    ai_label VARCHAR(100) NOT NULL,
    created_by_actor_type VARCHAR(20) NOT NULL,
    created_by_actor_id VARCHAR(150) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_proposal_assets_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_proposal_assets_quote
    ON proposal_assets (tenant_id, quote_id, created_at DESC);

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'lookup_customer',
    'register_quote_asset',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_crm_agent'
  AND status = 'ACTIVE';
