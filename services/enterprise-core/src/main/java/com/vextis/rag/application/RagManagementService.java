package com.vextis.rag.application;

import com.vextis.rag.RagChunk;
import com.vextis.rag.RagChunkInput;
import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagDocument;
import com.vextis.rag.RagSearchResult;
import com.vextis.rag.application.port.RagDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RagManagementService implements RagDirectory {

    private final RagDocumentRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RagManagementService(RagDocumentRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagSearchResult> search(String tenantId, List<Double> queryEmbedding, int limit, double minScore) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return repository.searchSimilar(tenantId, queryEmbedding, limit, minScore);
    }

    @Override
    public RagDocument ingestDocument(
            String tenantId,
            String documentUri,
            String fileName,
            String contentType,
            String contentHash,
            List<RagChunkInput> chunks
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (documentUri == null || documentUri.isBlank()) {
            throw new IllegalArgumentException("documentUri must not be blank");
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

        Instant now = clock.instant();
        Optional<RagDocument> existing = repository.findByUri(tenantId, documentUri);

        if (existing.isPresent()) {
            RagDocument current = existing.get();
            if (current.contentHash().equals(contentHash)) {
                // Idempotent: return existing document without re-processing
                return current;
            }

            // New version of existing document
            RagDocument updated = new RagDocument(
                    current.id(),
                    tenantId,
                    documentUri,
                    fileName,
                    contentType,
                    contentHash,
                    current.version() + 1,
                    RagDocument.Status.INDEXED,
                    chunks != null ? chunks.size() : 0,
                    current.createdAt(),
                    now
            );
            repository.update(updated);
            repository.deleteChunksForDocument(tenantId, current.id());

            if (chunks != null && !chunks.isEmpty()) {
                List<RagChunk> chunkEntities = toChunkEntities(tenantId, current.id(), chunks, now);
                repository.saveChunks(chunkEntities);
            }
            return updated;
        }

        // New document
        UUID documentId = UUID.randomUUID();
        RagDocument created = new RagDocument(
                documentId,
                tenantId,
                documentUri,
                fileName,
                contentType,
                contentHash,
                1,
                RagDocument.Status.INDEXED,
                chunks != null ? chunks.size() : 0,
                now,
                now
        );
        repository.save(created);

        if (chunks != null && !chunks.isEmpty()) {
            List<RagChunk> chunkEntities = toChunkEntities(tenantId, documentId, chunks, now);
            repository.saveChunks(chunkEntities);
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagDocument> listDocuments(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return repository.listAll(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RagDocument> findById(String tenantId, UUID documentId) {
        if (tenantId == null || tenantId.isBlank() || documentId == null) {
            return Optional.empty();
        }
        return repository.findById(tenantId, documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RagDocument> findByUri(String tenantId, String documentUri) {
        if (tenantId == null || tenantId.isBlank() || documentUri == null || documentUri.isBlank()) {
            return Optional.empty();
        }
        return repository.findByUri(tenantId, documentUri);
    }

    private List<RagChunk> toChunkEntities(
            String tenantId,
            UUID documentId,
            List<RagChunkInput> inputs,
            Instant now
    ) {
        return inputs.stream()
                .map(input -> {
                    String metadataJson = "{}";
                    if (input.metadata() != null && !input.metadata().isEmpty()) {
                        try {
                            metadataJson = objectMapper.writeValueAsString(input.metadata());
                        } catch (Exception ignored) {
                            metadataJson = "{}";
                        }
                    }
                    return new RagChunk(
                            UUID.randomUUID(),
                            documentId,
                            tenantId,
                            input.chunkIndex(),
                            input.chunkText(),
                            input.tokenCount(),
                            input.embedding(),
                            metadataJson,
                            now
                    );
                })
                .toList();
    }
}
