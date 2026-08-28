package com.vextis.rag.application.port;

import com.vextis.rag.RagChunk;
import com.vextis.rag.RagDocument;
import com.vextis.rag.RagSearchResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RagDocumentRepository {

    void save(RagDocument document);

    void update(RagDocument document);

    Optional<RagDocument> findById(String tenantId, UUID documentId);

    Optional<RagDocument> findByUri(String tenantId, String documentUri);

    Optional<RagDocument> findByHash(String tenantId, String contentHash);

    List<RagDocument> listAll(String tenantId);

    void deleteChunksForDocument(String tenantId, UUID documentId);

    void saveChunks(List<RagChunk> chunks);

    List<RagSearchResult> searchSimilar(
            String tenantId,
            String embeddingSpace,
            List<Double> embedding,
            int limit,
            double minScore
    );
}
