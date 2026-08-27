package com.vextis.rag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RagDirectory {

    List<RagSearchResult> search(String tenantId, List<Double> queryEmbedding, int limit, double minScore);

    RagDocument ingestDocument(
            String tenantId,
            String documentUri,
            String fileName,
            String contentType,
            String contentHash,
            List<RagChunkInput> chunks
    );

    List<RagDocument> listDocuments(String tenantId);

    Optional<RagDocument> findById(String tenantId, UUID documentId);

    Optional<RagDocument> findByUri(String tenantId, String documentUri);
}
