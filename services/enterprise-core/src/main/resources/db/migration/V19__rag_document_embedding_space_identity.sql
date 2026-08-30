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

-- Derived from each document's own chunks (rag_document_chunks.embedding_space,
-- populated and constrained NOT NULL by V17) rather than assumed. Backfilling
-- every existing row with a hardcoded 'this all came from the demo seeder'
-- literal would be true in today's environment but is not something this
-- migration can know in general: a deployment that had already ingested real
-- Vertex documents before this point would have every one of them silently
-- mislabeled as mock, corrupting exactly the identity this migration exists to
-- establish.
--
-- A document with no chunks, or whose chunks disagree on embedding_space,
-- cannot be derived safely. The migration fails and names the offending
-- document ids rather than guessing, so a human repairs the specific row
-- (re-ingest it, or delete it if it is orphaned) instead of the system
-- mislabeling it.
DO $$
DECLARE
    undetermined_ids TEXT;
BEGIN
    SELECT string_agg(d.id::text, ', ' ORDER BY d.id)
    INTO undetermined_ids
    FROM rag_documents d
    WHERE (
        SELECT COUNT(DISTINCT c.embedding_space)
        FROM rag_document_chunks c
        WHERE c.document_id = d.id
    ) <> 1;

    IF undetermined_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Cannot derive embedding_space for rag_documents row(s) [%]: each document must have at least one '
            'chunk, and every one of its chunks must agree on exactly one embedding_space. Repair the affected '
            'document(s) — re-ingest with a consistent embedder, or delete the row if it is orphaned — before '
            'this migration can run.',
            undetermined_ids;
    END IF;

    UPDATE rag_documents d
    SET embedding_space = derived.space
    FROM (
        SELECT document_id, MIN(embedding_space) AS space
        FROM rag_document_chunks
        GROUP BY document_id
    ) AS derived
    WHERE d.id = derived.document_id;
END $$;

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
