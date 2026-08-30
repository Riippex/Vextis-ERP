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
    private static final String MOCK_SPACE = "mock-sha256:sha256-v1:768";
    private static final String URI = "gs://bucket/policy.pdf";

    private final RagDocumentRepository repository = mock(RagDocumentRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
    private RagManagementService service;

    @BeforeEach
    void setup() {
        service = new RagManagementService(repository, objectMapper, clock);
    }

    private static RagChunkInput chunk(String text, String space) {
        return new RagChunkInput(0, text, 5, Collections.nCopies(768, 0.1), space, Map.of());
    }

    private static RagDocument indexed(UUID id, String space, String contentHash) {
        return new RagDocument(
                id, "demo-tenant", URI, "policy.pdf", "application/pdf", contentHash, space,
                1, RagDocument.Status.INDEXED, 1,
                Instant.parse("2026-08-27T08:00:00Z"), Instant.parse("2026-08-27T08:00:00Z"));
    }

    private RagDocument ingest(String space, String contentHash) {
        return service.ingestDocument(
                "demo-tenant", URI, "policy.pdf", "application/pdf", contentHash, space,
                List.of(chunk("Policy content here", space)));
    }

    @Test
    void ingestsNewDocumentAndSavesChunks() {
        when(repository.findByUri("demo-tenant", URI, VERTEX_SPACE)).thenReturn(Optional.empty());

        RagDocument doc = ingest(VERTEX_SPACE, "hash123");

        assertThat(doc.tenantId()).isEqualTo("demo-tenant");
        assertThat(doc.fileName()).isEqualTo("policy.pdf");
        assertThat(doc.embeddingSpace()).isEqualTo(VERTEX_SPACE);
        assertThat(doc.version()).isEqualTo(1);
        assertThat(doc.status()).isEqualTo(RagDocument.Status.INDEXED);

        verify(repository).save(any(RagDocument.class));
        verify(repository).saveChunks(any());
    }

    @Test
    void ingestionIsIdempotentWithinTheSameEmbeddingSpace() {
        UUID docId = UUID.randomUUID();
        when(repository.findByUri("demo-tenant", URI, VERTEX_SPACE))
                .thenReturn(Optional.of(indexed(docId, VERTEX_SPACE, "hash123")));

        RagDocument result = ingest(VERTEX_SPACE, "hash123");

        assertThat(result.id()).isEqualTo(docId);
        verify(repository, never()).save(any());
        verify(repository, never()).update(any());
        verify(repository, never()).saveChunks(any());
    }

    @Test
    void reIndexingTheSameDocumentInAnotherSpaceIsANewIndexation() {
        // The regression: identity was tenant + URI, so the content hash matched
        // the mock indexation and re-ingesting under Vertex returned the old row
        // untouched. Every Vertex query then found nothing, silently.
        when(repository.findByUri("demo-tenant", URI, MOCK_SPACE))
                .thenReturn(Optional.of(indexed(UUID.randomUUID(), MOCK_SPACE, "hash123")));
        when(repository.findByUri("demo-tenant", URI, VERTEX_SPACE)).thenReturn(Optional.empty());

        RagDocument reindexed = ingest(VERTEX_SPACE, "hash123");

        assertThat(reindexed.embeddingSpace()).isEqualTo(VERTEX_SPACE);
        assertThat(reindexed.version()).isEqualTo(1);
        verify(repository).save(any(RagDocument.class));
        verify(repository).saveChunks(any());
    }

    @Test
    void lookingUpAnIndexationIsScopedToItsEmbeddingSpace() {
        when(repository.findByUri("demo-tenant", URI, MOCK_SPACE))
                .thenReturn(Optional.of(indexed(UUID.randomUUID(), MOCK_SPACE, "hash123")));
        when(repository.findByUri("demo-tenant", URI, VERTEX_SPACE)).thenReturn(Optional.empty());

        assertThat(service.findByUri("demo-tenant", URI, MOCK_SPACE)).isPresent();
        assertThat(service.findByUri("demo-tenant", URI, VERTEX_SPACE)).isEmpty();
    }

    @Test
    void savedDocumentAndChunksAgreeOnTheEmbeddingSpace() {
        when(repository.findByUri("demo-tenant", URI, VERTEX_SPACE)).thenReturn(Optional.empty());

        ingest(VERTEX_SPACE, "hash123");

        ArgumentCaptor<RagDocument> saved = ArgumentCaptor.captor();
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().embeddingSpace()).isEqualTo(VERTEX_SPACE);

        ArgumentCaptor<List<RagChunk>> chunks = ArgumentCaptor.captor();
        verify(repository).saveChunks(chunks.capture());
        assertThat(chunks.getValue())
                .singleElement()
                .extracting(RagChunk::embeddingSpace)
                .isEqualTo(VERTEX_SPACE);
    }

    @Test
    void chunksFromAnotherSpaceAreRejectedBeforeAnythingIsWritten() {
        // A chunk labelled with a different space would be invisible to a query
        // for the document space, so the payload is incoherent rather than mixed.
        assertThatThrownBy(() -> service.ingestDocument(
                "demo-tenant", URI, "policy.pdf", "application/pdf", "hash123", VERTEX_SPACE,
                List.of(chunk("Policy content here", MOCK_SPACE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MOCK_SPACE);

        verify(repository, never()).save(any());
        verify(repository, never()).saveChunks(any());
    }

    @Test
    void ingestionWithoutAnEmbeddingSpaceIsRejected() {
        assertThatThrownBy(() -> service.ingestDocument(
                "demo-tenant", URI, "policy.pdf", "application/pdf", "hash123", "  ",
                List.of(chunk("Policy content here", VERTEX_SPACE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingSpace must not be blank");
    }

    @Test
    void updatesVersionAndReplacesChunksWhenContentHashChangesInTheSameSpace() {
        UUID docId = UUID.randomUUID();
        when(repository.findByUri("demo-tenant", URI, VERTEX_SPACE))
                .thenReturn(Optional.of(indexed(docId, VERTEX_SPACE, "old_hash")));

        RagDocument result = ingest(VERTEX_SPACE, "new_hash_456");

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.contentHash()).isEqualTo("new_hash_456");
        assertThat(result.embeddingSpace()).isEqualTo(VERTEX_SPACE);
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
    void rejectsADocumentWithoutAnEmbeddingSpace() {
        assertThatThrownBy(() -> new RagDocument(
                UUID.randomUUID(), "demo-tenant", URI, "policy.pdf", "application/pdf", "hash",
                null, 1, RagDocument.Status.INDEXED, 1, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingSpace must not be blank");
    }

    @Test
    void listsEveryIndexationOfADocumentIncludingOnePerSpace() {
        UUID mockId = UUID.randomUUID();
        UUID vertexId = UUID.randomUUID();
        when(repository.listAll("demo-tenant")).thenReturn(List.of(
                indexed(mockId, MOCK_SPACE, "hash123"),
                indexed(vertexId, VERTEX_SPACE, "hash123")));

        assertThat(service.listDocuments("demo-tenant"))
                .extracting(RagDocument::embeddingSpace)
                .containsExactly(MOCK_SPACE, VERTEX_SPACE);
        verify(repository).listAll(eq("demo-tenant"));
    }
}
