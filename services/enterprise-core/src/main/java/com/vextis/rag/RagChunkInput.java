package com.vextis.rag;

import java.util.List;
import java.util.Map;

public record RagChunkInput(
        int chunkIndex,
        String chunkText,
        int tokenCount,
        List<Double> embedding,
        String embeddingSpace,
        Map<String, Object> metadata
) {
    public RagChunkInput {
        if (embeddingSpace == null || embeddingSpace.isBlank()) {
            throw new IllegalArgumentException("embeddingSpace must not be blank");
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
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
