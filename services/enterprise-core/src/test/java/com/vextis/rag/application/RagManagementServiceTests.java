package com.vextis.rag.application;

import com.vextis.rag.RagChunk;
import com.vextis.rag.RagChunkInput;
import com.vextis.rag.RagDocument;
import com.vextis.rag.RagSearchResult;
import com.vextis.rag.application.port.RagDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagManagementServiceTests {

    private static final String VERTEX_SPACE = "vertex:text-embedding-004:768";

    private final RagDocumentRepository repository = mock(RagDocumentRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
    private RagManagementService service;

    @BeforeEach
    void setup() {
        service = new RagManagementService(repository, objectMapper, clock);
    }

    @Test
    void ingestsNewDocumentAndSavesChunks() {
        when(repository.findByUri("demo-tenant", "gs://bucket/policy.pdf")).thenReturn(Optional.empty());

        List<Double> embedding = Collections.nCopies(768, 0.1);
        RagChunkInput chunkInput = new RagChunkInput(0, "Policy content here", 10, embedding, VERTEX_SPACE, Map.of("section", 1));

        RagDocument doc = service.ingestDocument(
                "demo-tenant",
                "gs://bucket/policy.pdf",
                "policy.pdf",
                "application/pdf",
                "hash123",
                List.of(chunkInput)
        );

        assertThat(doc.tenantId()).isEqualTo("demo-tenant");
        assertThat(doc.fileName()).isEqualTo("policy.pdf");
        assertThat(doc.version()).isEqualTo(1);
        assertThat(doc.status()).isEqualTo(RagDocument.Status.INDEXED);

        verify(repository).save(any(RagDocument.class));
        verify(repository).saveChunks(any());
    }

    @Test
    void ingestionIsIdempotentWhenContentHashMatches() {
        UUID docId = UUID.randomUUID();
        RagDocument existing = new RagDocument(
                docId,
                "demo-tenant",
                "gs://bucket/policy.pdf",
                "policy.pdf",
                "application/pdf",
                "hash123",
                1,
                RagDocument.Status.INDEXED,
                1,
                Instant.parse("2026-08-27T08:00:00Z"),
                Instant.parse("2026-08-27T08:00:00Z")
        );
        when(repository.findByUri("demo-tenant", "gs://bucket/policy.pdf")).thenReturn(Optional.of(existing));

        RagDocument result = service.ingestDocument(
                "demo-tenant",
                "gs://bucket/policy.pdf",
                "policy.pdf",
                "application/pdf",
                "hash123",
                List.of(new RagChunkInput(0, "New chunk", 5, List.of(0.1), VERTEX_SPACE, Map.of()))
        );

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any());
        verify(repository, never()).update(any());
        verify(repository, never()).saveChunks(any());
    }

    @Test
    void updatesVersionAndReplacesChunksWhenContentHashChanges() {
        UUID docId = UUID.randomUUID();
        RagDocument existing = new RagDocument(
                docId,
                "demo-tenant",
                "gs://bucket/policy.pdf",
                "policy.pdf",
                "application/pdf",
                "old_hash",
                1,
                RagDocument.Status.INDEXED,
                1,
                Instant.parse("2026-08-27T08:00:00Z"),
                Instant.parse("2026-08-27T08:00:00Z")
        );
        when(repository.findByUri("demo-tenant", "gs://bucket/policy.pdf")).thenReturn(Optional.of(existing));

        List<Double> embedding = Collections.nCopies(768, 0.2);
        RagDocument result = service.ingestDocument(
                "demo-tenant",
                "gs://bucket/policy.pdf",
                "policy.pdf",
                "application/pdf",
                "new_hash_456",
                List.of(new RagChunkInput(0, "Updated content", 8, embedding, VERTEX_SPACE, Map.of()))
        );

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.contentHash()).isEqualTo("new_hash_456");
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-08-27T10:00:00Z"));

        verify(repository).update(any(RagDocument.class));
        verify(repository).deleteChunksForDocument("demo-tenant", docId);
        verify(repository).saveChunks(any());
    }

    @Test
    void searchDelegatesToRepositoryWithTenantIsolation() {
        List<Double> embedding = Collections.nCopies(768, 0.1);
        when(repository.searchSimilar("demo-tenant", VERTEX_SPACE, embedding, 5, 0.5)).thenReturn(List.of(
                new RagSearchResult(
                        UUID.randomUUID(),
                        "policy.pdf",
                        "gs://bucket/policy.pdf",
                        0,
                        "Found text",
                        0.85,
                        Map.of()
                )
        ));

        List<RagSearchResult> results = service.search("demo-tenant", VERTEX_SPACE, embedding, 5, 0.5);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().similarityScore()).isEqualTo(0.85);

        verify(repository).searchSimilar("demo-tenant", VERTEX_SPACE, embedding, 5, 0.5);
    }

    @Test
    void rejectsBlankTenant() {
        assertThatThrownBy(() -> service.search("", VERTEX_SPACE, List.of(0.1), 5, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    void rejectsSearchWithoutAnEmbeddingSpace() {
        assertThatThrownBy(() -> service.search("demo-tenant", "  ", List.of(0.1), 5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingSpace must not be blank");
    }

    @Test
    void rejectsChunksWithoutAnEmbeddingSpace() {
        assertThatThrownBy(() -> new RagChunkInput(0, "text", 1, List.of(0.1), "", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingSpace must not be blank");
    }

    @Test
    void storedChunksCarryTheEmbeddingSpaceTheyWereProducedIn() {
        when(repository.findByUri("demo-tenant", "gs://bucket/policy.pdf")).thenReturn(Optional.empty());

        service.ingestDocument(
                "demo-tenant",
                "gs://bucket/policy.pdf",
                "policy.pdf",
                "application/pdf",
                "hash123",
                List.of(new RagChunkInput(0, "Policy content", 3, Collections.nCopies(768, 0.1), VERTEX_SPACE, Map.of()))
        );

        ArgumentCaptor<List<RagChunk>> saved = ArgumentCaptor.captor();
        verify(repository).saveChunks(saved.capture());
        assertThat(saved.getValue())
                .singleElement()
                .extracting(RagChunk::embeddingSpace)
                .isEqualTo(VERTEX_SPACE);
    }
}
