-- Governed document ingestion needs an identity of its own: the retrieval
-- agents may read the knowledge base but must not be able to write to it, so
-- ingest_knowledge_document is granted to a dedicated registry entry rather
-- than added to the coordinator allowlist.
--
-- It shares the coordinator service identity because Enterprise Core
-- authorizes /internal/agent-tools/** on that single trusted caller; the tool
-- allowlist, not the transport identity, is what separates the two roles.

INSERT INTO agent_registry_entries (
    tenant_id, agent_id, version, display_name, department, purpose, framework,
    model_id, prompt_version, service_identity, status, capabilities, allowed_tools
) VALUES (
    'demo-tenant', 'vextis_document_ingestor', '1.0.0', 'Document Ingestor',
    'CROSS_DEPARTMENT', 'Chunks and embeds approved documents into the tenant knowledge base.',
    'GOOGLE_ADK', 'text-embedding-004', '1.0.0', 'coordinator-agent', 'ACTIVE',
    ARRAY['document chunking', 'embedding', 'knowledge ingestion'],
    ARRAY['ingest_knowledge_document']
);
