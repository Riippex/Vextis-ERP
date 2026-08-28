package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.rag.RagDirectory;
import com.vextis.rag.RagSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
