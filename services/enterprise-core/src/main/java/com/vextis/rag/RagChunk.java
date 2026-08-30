package com.vextis.rag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RagChunk(
        UUID id,
        UUID documentId,
        String tenantId,
        int chunkIndex,
        String chunkText,
        int tokenCount,
        List<Double> embedding,
        String embeddingSpace,
        String metadataJson,
        Instant createdAt
) {
    public RagChunk {
        if (embeddingSpace == null || embeddingSpace.isBlank()) {
            throw new IllegalArgumentException("embeddingSpace must not be blank");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (chunkText == null || chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must not be negative");
        }
        if (metadataJson == null) {
            metadataJson = "{}";
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
