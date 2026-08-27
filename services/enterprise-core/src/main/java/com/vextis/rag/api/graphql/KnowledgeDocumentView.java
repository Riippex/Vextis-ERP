package com.vextis.rag.api.graphql;

import com.vextis.rag.RagDocument;

import java.util.UUID;

public record KnowledgeDocumentView(
        UUID id,
        String documentUri,
        String fileName,
        String contentType,
        String contentHash,
        int version,
        String status,
        int chunkCount,
        String createdAt,
        String updatedAt
) {
    public static KnowledgeDocumentView from(RagDocument doc) {
        return new KnowledgeDocumentView(
                doc.id(),
                doc.documentUri(),
                doc.fileName(),
                doc.contentType(),
                doc.contentHash(),
                doc.version(),
                doc.status().name(),
                doc.chunkCount(),
                doc.createdAt().toString(),
                doc.updatedAt().toString()
        );
    }
}
