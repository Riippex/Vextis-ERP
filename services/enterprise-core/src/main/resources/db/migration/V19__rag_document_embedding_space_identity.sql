-- An indexation is identified by tenant, document URI and the embedding space
-- it was produced in, not by tenant and URI alone.
--
-- V17 recorded the space on each chunk but left document identity unchanged, so
-- ingesting the same document again under a different embedder hit the
-- content-hash idempotency check in RagManagementService and returned the
-- existing row untouched. Re-indexing a corpus after switching from the mock
-- embedder to Vertex therefore did nothing at all, and every query in the new
-- space silently found no evidence.
--
-- With the space in the key, the same document can be indexed once per space:
-- a migration writes a second indexation instead of being swallowed, and the
-- old one stays retrievable until it is deleted.

ALTER TABLE rag_documents
    ADD COLUMN embedding_space VARCHAR(120);

-- Everything indexed before this point came from the demo seeder.
UPDATE rag_documents
SET embedding_space = 'mock-sha256:sha256-v1:768'
WHERE embedding_space IS NULL;

ALTER TABLE rag_documents
    ALTER COLUMN embedding_space SET NOT NULL;

ALTER TABLE rag_documents
    ADD CONSTRAINT ck_rag_document_embedding_space CHECK (length(trim(embedding_space)) > 0);

ALTER TABLE rag_documents
    DROP CONSTRAINT uq_rag_document_uri;

ALTER TABLE rag_documents
    ADD CONSTRAINT uq_rag_document_uri_space UNIQUE (tenant_id, document_uri, embedding_space);

CREATE INDEX ix_rag_documents_tenant_space
    ON rag_documents (tenant_id, embedding_space);
