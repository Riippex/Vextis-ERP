CREATE TABLE rag_documents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    document_uri VARCHAR(1000) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'INDEXED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rag_document_uri UNIQUE (tenant_id, document_uri),
    CONSTRAINT ck_rag_document_uri CHECK (document_uri LIKE 'gs://%' OR document_uri LIKE 'urn:%'),
    CONSTRAINT ck_rag_document_status CHECK (status IN ('INDEXED', 'PENDING', 'FAILED')),
    CONSTRAINT ck_rag_document_version CHECK (version >= 1)
);

CREATE TABLE rag_document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
    tenant_id VARCHAR(100) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    token_count INTEGER NOT NULL,
    embedding vector(768),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rag_chunk_index UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_rag_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_rag_token_count CHECK (token_count >= 0)
);

CREATE INDEX ix_rag_documents_tenant_status
    ON rag_documents (tenant_id, status);

CREATE INDEX ix_rag_chunks_tenant_doc
    ON rag_document_chunks (tenant_id, document_id);

CREATE INDEX ix_rag_chunks_embedding
    ON rag_document_chunks USING hnsw (embedding vector_cosine_ops);

UPDATE agent_registry_entries
SET capabilities = ARRAY['department routing', 'cross-department coordination', 'knowledge retrieval'],
    allowed_tools = ARRAY['search_knowledge_base']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_coordinator'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['lookup_customer', 'search_knowledge_base']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_crm_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['get_stock', 'search_knowledge_base']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_inventory_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['get_credit', 'create_invoice', 'search_knowledge_base']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_billing_agent'
  AND status = 'ACTIVE';
