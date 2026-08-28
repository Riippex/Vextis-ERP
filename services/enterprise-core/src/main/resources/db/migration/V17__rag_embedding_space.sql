-- Documents and queries must be compared inside one embedding space. Before
-- this migration nothing recorded which model produced a stored vector, so the
-- SHA-256 mock vectors the demo seeder writes and a real Vertex query embedding
-- would be compared against each other and return meaningless nearest
-- neighbours with plausible-looking cosine scores.
--
-- Every chunk that exists at this point came from DemoSeedingService, whose
-- vectors are the deterministic SHA-256 mock, so that is the correct backfill.
-- Search now filters on this column, which means a Vertex query simply cannot
-- match a mock chunk.

ALTER TABLE rag_document_chunks
    ADD COLUMN embedding_space VARCHAR(120);

UPDATE rag_document_chunks
SET embedding_space = 'mock-sha256:sha256-v1:768'
WHERE embedding_space IS NULL;

ALTER TABLE rag_document_chunks
    ALTER COLUMN embedding_space SET NOT NULL;

ALTER TABLE rag_document_chunks
    ADD CONSTRAINT ck_rag_chunk_embedding_space CHECK (length(trim(embedding_space)) > 0);

CREATE INDEX ix_rag_chunks_tenant_space
    ON rag_document_chunks (tenant_id, embedding_space);
