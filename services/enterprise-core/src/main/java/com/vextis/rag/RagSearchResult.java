package com.vextis.rag;

import java.util.Map;
import java.util.UUID;

public record RagSearchResult(
        UUID documentId,
        String fileName,
        String documentUri,
        int chunkIndex,
        String chunkText,
        double similarityScore,
        Map<String, Object> metadata
) {
    public RagSearchResult {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (documentUri == null || documentUri.isBlank()) {
            throw new IllegalArgumentException("documentUri must not be blank");
        }
        if (chunkText == null || chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
