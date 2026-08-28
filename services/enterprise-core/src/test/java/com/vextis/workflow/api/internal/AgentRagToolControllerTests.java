package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.rag.RagChunkInput;
import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagDocument;
import com.vextis.rag.RagSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentRagToolController.class)
@Import(AgentToolAuthorizer.class)
class AgentRagToolControllerTests {

    private static final String VERTEX_SPACE = "vertex:text-embedding-004:768";
    private static final String CONTENT_HASH =
            "9f2b1c4e6a8d0f3b5c7e9a1d3f5b7c9e1a3d5f7b9c1e3a5d7f9b1c3e5a7d9f1b";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagDirectory ragDirectory;

    @MockitoBean
    private AgentDirectory agents;

    private List<Double> testEmbedding;

    @BeforeEach
    void setup() {
        testEmbedding = Collections.nCopies(768, 0.05);
        when(agents.findActive("demo-tenant", "vextis_coordinator")).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_coordinator", "1.0.0", "Coordinator", "CROSS_DEPARTMENT", "purpose",
                        "GOOGLE_ADK", "gemini-3.5-flash", "1.0.0", "coordinator-agent", "ACTIVE",
                        List.of(), List.of("search_knowledge_base"))));
    }

    @Test
    void authenticatedCoordinatorCanSearchKnowledgeBase() throws Exception {
        UUID docId = UUID.randomUUID();
        when(ragDirectory.search(eq("demo-tenant"), eq(VERTEX_SPACE), anyList(), eq(5), eq(0.55))).thenReturn(List.of(
                new RagSearchResult(
                        docId,
                        "commercial_terms.pdf",
                        "gs://vextis-demo/docs/commercial_terms.pdf",
                        0,
                        "Standard payment terms are net 30 days for tier 1 customers.",
                        0.92,
                        Map.of("category", "commercial")
                )
        ));

        String requestJson = """
                {
                    "query": "payment terms",
                    "embedding": %s,
                    "embeddingSpace": "vertex:text-embedding-004:768",
                    "limit": 5,
                    "minScore": 0.0
                }
                """.formatted(testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/search")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_coordinator")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].documentId").value(docId.toString()))
                .andExpect(jsonPath("$.matches[0].fileName").value("commercial_terms.pdf"))
                .andExpect(jsonPath("$.matches[0].chunkText").value("Standard payment terms are net 30 days for tier 1 customers."))
                .andExpect(jsonPath("$.matches[0].similarityScore").value(0.92))
                .andExpect(jsonPath("$.matches[0].metadata.category").value("commercial"));

        // A caller asking for 0.0 still gets the configured floor.
        verify(ragDirectory).search(eq("demo-tenant"), eq(VERTEX_SPACE), anyList(), eq(5), eq(0.55));
    }

    @Test
    void rejectsWhenServiceTokenIsMissing() throws Exception {
        String requestJson = """
                {
                    "embedding": %s,
                    "embeddingSpace": "vertex:text-embedding-004:768"
                }
                """.formatted(testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/search")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_coordinator")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ragDirectory);
    }

    @Test
    void rejectsWhenAgentLacksSearchKnowledgeTool() throws Exception {
        when(agents.findActive("demo-tenant", "vextis_unauthorized_agent")).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_unauthorized_agent", "1.0.0", "Unauthorized", "OTHER", "purpose",
                        "GOOGLE_ADK", "gemini-3.5-flash", "1.0.0", "coordinator-agent", "ACTIVE",
                        List.of(), List.of("other_tool"))));

        String requestJson = """
                {
                    "embedding": %s,
                    "embeddingSpace": "vertex:text-embedding-004:768"
                }
                """.formatted(testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/search")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_unauthorized_agent")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ragDirectory);
    }

    @Test
    void rejectsSearchWithoutAnEmbeddingSpace() throws Exception {
        String requestJson = """
                {
                    "query": "payment terms",
                    "embedding": %s
                }
                """.formatted(testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/search")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_coordinator")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ragDirectory);
    }

    @Test
    void queriesFromOneEmbeddingSpaceNeverReachChunksFromAnother() throws Exception {
        // A mock-embedded query must not be answered with Vertex-embedded
        // chunks or the other way round: the space travels with the request and
        // the search is scoped to it.
        when(ragDirectory.search(eq("demo-tenant"), eq("mock-sha256:sha256-v1:768"), anyList(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        String requestJson = """
                {
                    "query": "payment terms",
                    "embedding": %s,
                    "embeddingSpace": "mock-sha256:sha256-v1:768"
                }
                """.formatted(testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/search")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_coordinator")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isEmpty());

        verify(ragDirectory).search(
                eq("demo-tenant"), eq("mock-sha256:sha256-v1:768"), anyList(), anyInt(), anyDouble());
        verify(ragDirectory, never()).search(eq("demo-tenant"), eq(VERTEX_SPACE), anyList(), anyInt(), anyDouble());
    }

    @Test
    void ingestorAgentCanWriteAGovernedKnowledgeDocument() throws Exception {
        UUID docId = UUID.randomUUID();
        when(agents.findActive("demo-tenant", "vextis_document_ingestor")).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_document_ingestor", "1.0.0", "Ingestor", "CROSS_DEPARTMENT", "purpose",
                        "GOOGLE_ADK", "text-embedding-004", "1.0.0", "coordinator-agent", "ACTIVE",
                        List.of(), List.of("ingest_knowledge_document"))));
        when(ragDirectory.ingestDocument(eq("demo-tenant"), any(), any(), any(), any(), anyList()))
                .thenReturn(new RagDocument(
                        docId, "demo-tenant", "urn:vextis:policy:commercial", "commercial_policy.md",
                        "text/markdown", CONTENT_HASH, 1, RagDocument.Status.INDEXED, 1,
                        Instant.parse("2026-08-27T10:00:00Z"), Instant.parse("2026-08-27T10:00:00Z")));

        mockMvc.perform(post("/internal/agent-tools/v1/rag/documents")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_document_ingestor")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestRequestJson(VERTEX_SPACE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("INDEXED"));

        ArgumentCaptor<List<RagChunkInput>> chunks = ArgumentCaptor.captor();
        verify(ragDirectory).ingestDocument(
                eq("demo-tenant"), eq("urn:vextis:policy:commercial"), eq("commercial_policy.md"),
                eq("text/markdown"), eq(CONTENT_HASH), chunks.capture());
        assertThat(chunks.getValue())
                .singleElement()
                .extracting(RagChunkInput::embeddingSpace)
                .isEqualTo(VERTEX_SPACE);
    }

    @Test
    void readOnlyAgentsCannotWriteToTheKnowledgeBase() throws Exception {
        // vextis_coordinator holds search_knowledge_base, not
        // ingest_knowledge_document: reading the index must not imply writing it.
        mockMvc.perform(post("/internal/agent-tools/v1/rag/documents")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_coordinator")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestRequestJson(VERTEX_SPACE)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ragDirectory);
    }

    @Test
    void rejectsIngestionWithoutAnEmbeddingSpace() throws Exception {
        String requestJson = """
                {
                    "documentUri": "urn:vextis:policy:commercial",
                    "fileName": "commercial_policy.md",
                    "contentType": "text/markdown",
                    "contentHash": "%s",
                    "chunks": [{"chunkIndex": 0, "chunkText": "Net 30.", "tokenCount": 2, "embedding": %s}]
                }
                """.formatted(CONTENT_HASH, testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/documents")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_document_ingestor")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ragDirectory);
    }

    private String ingestRequestJson(String embeddingSpace) {
        return """
                {
                    "documentUri": "urn:vextis:policy:commercial",
                    "fileName": "commercial_policy.md",
                    "contentType": "text/markdown",
                    "contentHash": "%s",
                    "embeddingSpace": "%s",
                    "chunks": [
                        {
                            "chunkIndex": 0,
                            "chunkText": "Standard payment terms are Net 30 days.",
                            "tokenCount": 9,
                            "embedding": %s,
                            "metadata": {"section": "commercial"}
                        }
                    ]
                }
                """.formatted(CONTENT_HASH, embeddingSpace, testEmbedding.toString());
    }

    @Test
    void rejectsWhenTenantDoesNotMatch() throws Exception {
        String requestJson = """
                {
                    "embedding": %s,
                    "embeddingSpace": "vertex:text-embedding-004:768"
                }
                """.formatted(testEmbedding.toString());

        mockMvc.perform(post("/internal/agent-tools/v1/rag/search")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "foreign-tenant")
                        .header("X-Agent-Id", "vextis_coordinator")
                        .header("X-Correlation-Id", "01J...")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ragDirectory);
    }
}
