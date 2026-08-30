package com.vextis.workflow.api.internal;

import com.vextis.rag.RagChunkInput;
import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagDocument;
import com.vextis.rag.RagSearchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/rag")
class AgentRagToolController {

    private final RagDirectory ragDirectory;
    private final AgentToolAuthorizer authorizer;
    private final double defaultMinScore;

    AgentRagToolController(
            RagDirectory ragDirectory,
            AgentToolAuthorizer authorizer,
            @Value("${vextis.rag.min-similarity:0.55}") double defaultMinScore
    ) {
        this.ragDirectory = ragDirectory;
        this.authorizer = authorizer;
        this.defaultMinScore = defaultMinScore;
    }

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    SearchKnowledgeResponse search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestBody @Valid SearchKnowledgeRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.SEARCH_KNOWLEDGE_BASE);

        int limit = request.limit() != null ? request.limit() : 5;
        // A 0.0 floor returns the nearest chunks no matter how unrelated they
        // are, which reads as grounded evidence downstream. Callers may raise
        // the bar but not remove it.
        double minScore = request.minScore() != null
                ? Math.max(request.minScore(), defaultMinScore)
                : defaultMinScore;

        List<RagSearchResult> results = ragDirectory.search(
                tenantId, request.embeddingSpace(), request.embedding(), limit, minScore);

        List<KnowledgeChunkMatchResponse> matches = results.stream()
                .map(KnowledgeChunkMatchResponse::from)
                .toList();

        return new SearchKnowledgeResponse(matches);
    }

    /**
     * {@code embeddingSpace} identifies the provider, model and dimension that
     * produced {@code embedding}. It is required because a query vector is only
     * comparable to chunks embedded the same way; without it a Vertex query and
     * a mock-embedded chunk would be scored against each other.
     */
    record SearchKnowledgeRequest(
            @Size(max = 1000) String query,
            @NotNull @Size(min = 768, max = 768) List<Double> embedding,
            @NotBlank @Size(max = 120) String embeddingSpace,
            @Min(1) @Max(20) Integer limit,
            @Min(0) @Max(1) Double minScore
    ) {}

    /**
     * Governed ingestion. Agent Runtime chunks and embeds a document, then hands
     * the result here: Enterprise Core stays the only writer of the knowledge
     * base and applies the same tenant and tool-allowlist checks it applies to
     * every other business mutation.
     *
     * <p>The embedding space is recorded per chunk so a later query can only
     * retrieve what was embedded the same way.
     */
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.OK)
    IngestKnowledgeDocumentResponse ingest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestBody @Valid IngestKnowledgeDocumentRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.INGEST_KNOWLEDGE_DOCUMENT);

        List<RagChunkInput> chunks = request.chunks().stream()
                .map(chunk -> new RagChunkInput(
                        chunk.chunkIndex(),
                        chunk.chunkText(),
                        chunk.tokenCount(),
                        chunk.embedding(),
                        request.embeddingSpace(),
                        chunk.metadata() == null ? Map.of() : chunk.metadata()))
                .toList();

        RagDocument document = ragDirectory.ingestDocument(
                tenantId,
                request.documentUri(),
                request.fileName(),
                request.contentType(),
                request.contentHash(),
                request.embeddingSpace(),
                chunks);

        return IngestKnowledgeDocumentResponse.from(document);
    }

    record IngestKnowledgeDocumentRequest(
            @NotBlank @Size(max = 1000)
            @Pattern(regexp = "^(gs://|urn:).+", message = "documentUri must start with gs:// or urn:")
            String documentUri,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$", message = "contentHash must be a SHA-256 hex digest")
            String contentHash,
            @NotBlank @Size(max = 120) String embeddingSpace,
            @NotEmpty @Size(max = 500) List<@Valid IngestKnowledgeChunk> chunks
    ) {}

    record IngestKnowledgeChunk(
            @Min(0) int chunkIndex,
            @NotBlank @Size(max = 8000) String chunkText,
            @Min(0) int tokenCount,
            @NotNull @Size(min = 768, max = 768) List<Double> embedding,
            Map<String, Object> metadata
    ) {}

    record IngestKnowledgeDocumentResponse(
            UUID documentId,
            String documentUri,
            int version,
            String status,
            int chunkCount
    ) {
        static IngestKnowledgeDocumentResponse from(RagDocument document) {
            return new IngestKnowledgeDocumentResponse(
                    document.id(),
                    document.documentUri(),
                    document.version(),
                    document.status().name(),
                    document.chunkCount());
        }
    }

    record SearchKnowledgeResponse(List<KnowledgeChunkMatchResponse> matches) {}

    record KnowledgeChunkMatchResponse(
            UUID documentId,
            String fileName,
            String documentUri,
            int chunkIndex,
            String chunkText,
            double similarityScore,
            Map<String, Object> metadata
    ) {
        static KnowledgeChunkMatchResponse from(RagSearchResult result) {
            return new KnowledgeChunkMatchResponse(
                    result.documentId(),
                    result.fileName(),
                    result.documentUri(),
                    result.chunkIndex(),
                    result.chunkText(),
                    result.similarityScore(),
                    result.metadata()
            );
        }
    }
}
