package com.vextis.workflow.api.internal;

import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagSearchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    AgentRagToolController(RagDirectory ragDirectory, AgentToolAuthorizer authorizer) {
        this.ragDirectory = ragDirectory;
        this.authorizer = authorizer;
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
        double minScore = request.minScore() != null ? request.minScore() : 0.0;

        List<RagSearchResult> results = ragDirectory.search(tenantId, request.embedding(), limit, minScore);

        List<KnowledgeChunkMatchResponse> matches = results.stream()
                .map(KnowledgeChunkMatchResponse::from)
                .toList();

        return new SearchKnowledgeResponse(matches);
    }

    record SearchKnowledgeRequest(
            @Size(max = 1000) String query,
            @NotNull @Size(min = 768, max = 768) List<Double> embedding,
            @Min(1) @Max(20) Integer limit,
            @Min(0) @Max(1) Double minScore
    ) {}

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
