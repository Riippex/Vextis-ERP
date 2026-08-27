package com.vextis.rag;

import java.time.Instant;
import java.util.UUID;

public record RagDocument(
        UUID id,
        String tenantId,
        String documentUri,
        String fileName,
        String contentType,
        String contentHash,
        int version,
        Status status,
        int chunkCount,
        Instant createdAt,
        Instant updatedAt
) {
    public RagDocument {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (documentUri == null || (!documentUri.startsWith("gs://") && !documentUri.startsWith("urn:"))) {
            throw new IllegalArgumentException("documentUri must start with gs:// or urn:");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("timestamps must not be null");
        }
    }

    public enum Status {
        INDEXED,
        PENDING,
        FAILED
    }
}
