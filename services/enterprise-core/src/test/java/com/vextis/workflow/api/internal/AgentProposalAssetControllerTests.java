package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.crm.ProposalAssetConflictException;
import com.vextis.crm.ProposalAssetDirectory;
import com.vextis.crm.RegisterProposalAssetUseCase;
import com.vextis.shared.ConfiguredServiceCallerIdentities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentProposalAssetController.class)
@Import({AgentToolAuthorizer.class, ConfiguredServiceCallerIdentities.class})
class AgentProposalAssetControllerTests {

    private static final UUID QUOTE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID ASSET_ID = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
    private static final String STORAGE_URI =
            "gs://vextis-erp-hackathon-assets/proposals/deadbeefdeadbeefdeadbeef/concept-chair.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterProposalAssetUseCase proposalAssetUseCase;

    @MockitoBean
    private AgentDirectory agents;

    @BeforeEach
    void authorizeCrmAgent() {
        when(agents.findActive("demo-tenant", "vextis_crm_agent")).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_crm_agent", "1.0.0", "CRM Specialist", "CRM_SALES", "Customer management and proposal assets",
                        "GOOGLE_ADK", "gemini-3.5-flash", "1.0.0", "coordinator-agent", "ACTIVE",
                        List.of(), List.of("lookup_customer", "register_quote_asset", "search_knowledge_base"))));
    }

    @Test
    void preflightSucceedsForValidQuote() throws Exception {
        when(proposalAssetUseCase.preflight(any(RegisterProposalAssetUseCase.PreflightCommand.class)))
                .thenReturn(new RegisterProposalAssetUseCase.PreflightResult(
                        QUOTE_ID, "proposals/deadbeef", "corr-001", true));

        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets/preflight", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(QUOTE_ID.toString()))
                .andExpect(jsonPath("$.authorized").value(true))
                .andExpect(jsonPath("$.tenantPrefix").value("proposals/deadbeef"))
                .andExpect(jsonPath("$.correlationId").value("corr-001"));

        verify(proposalAssetUseCase).preflight(any(RegisterProposalAssetUseCase.PreflightCommand.class));
    }

    @Test
    void preflightRejectsNonExistentQuote() throws Exception {
        when(proposalAssetUseCase.preflight(any(RegisterProposalAssetUseCase.PreflightCommand.class)))
                .thenThrow(new NoSuchElementException("No quote or order found for this tenant"));

        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets/preflight", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedCrmAgentCanRegisterProposalAsset() throws Exception {
        when(proposalAssetUseCase.registerAsset(any(RegisterProposalAssetUseCase.RegisterCommand.class)))
                .thenReturn(new ProposalAssetDirectory.ProposalAssetView(
                        ASSET_ID,
                        QUOTE_ID.toString(),
                        STORAGE_URI,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "3D render of ergonomic office chair in titanium grey",
                        "AI-Generated Proposal Concept",
                        "AGENT",
                        "vextis_crm_agent",
                        "corr-001",
                        Instant.parse("2026-08-28T16:00:00Z")
                ));

        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idemp-key-proposal-asset-001")
                        .content("""
                                {
                                  "storageUri": "%s",
                                  "mediaType": "IMAGE",
                                  "modelId": "imagen-3.0-generate-002",
                                  "promptSummary": "3D render of ergonomic office chair in titanium grey",
                                  "aiLabel": "AI-Generated Proposal Concept"
                                }
                                """.formatted(STORAGE_URI)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ASSET_ID.toString()))
                .andExpect(jsonPath("$.quoteId").value(QUOTE_ID.toString()))
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.modelId").value("imagen-3.0-generate-002"))
                .andExpect(jsonPath("$.aiLabel").value("AI-Generated Proposal Concept"));

        verify(proposalAssetUseCase).registerAsset(org.mockito.ArgumentMatchers.argThat(cmd ->
                cmd.tenantId().equals("demo-tenant")
                        && cmd.quoteId().equals(QUOTE_ID)
                        && cmd.modelId().equals("imagen-3.0-generate-002")
                        && cmd.storageUri().equals(STORAGE_URI)));
    }

    @Test
    void rejectsUnauthorizedTokenBeforeRegistering() throws Exception {
        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer invalid-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idemp-key-proposal-asset-001")
                        .content("""
                                {
                                  "storageUri": "%s",
                                  "mediaType": "IMAGE",
                                  "modelId": "imagen-3.0-generate-002",
                                  "promptSummary": "3D render",
                                  "aiLabel": "AI-Generated"
                                }
                                """.formatted(STORAGE_URI)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proposalAssetUseCase);
    }

    @Test
    void rejectsAssetForAQuoteNotOwnedByTenant() throws Exception {
        when(proposalAssetUseCase.registerAsset(any(RegisterProposalAssetUseCase.RegisterCommand.class)))
                .thenThrow(new NoSuchElementException("No quote or order found for this tenant"));

        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idemp-key-proposal-asset-002")
                        .content("""
                                {
                                  "storageUri": "%s",
                                  "mediaType": "IMAGE",
                                  "modelId": "imagen-3.0-generate-002",
                                  "promptSummary": "3D render",
                                  "aiLabel": "AI-Generated"
                                }
                                """.formatted(STORAGE_URI)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAssetOutsideTheAuthorizedBucketOrPrefix() throws Exception {
        when(proposalAssetUseCase.registerAsset(any(RegisterProposalAssetUseCase.RegisterCommand.class)))
                .thenThrow(new IllegalArgumentException("Proposal asset URI does not belong to the authorized tenant bucket or prefix"));

        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idemp-key-proposal-asset-003")
                        .content("""
                                {
                                  "storageUri": "gs://some-other-bucket/whatever.png",
                                  "mediaType": "IMAGE",
                                  "modelId": "imagen-3.0-generate-002",
                                  "promptSummary": "3D render",
                                  "aiLabel": "AI-Generated"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictWhenIdempotencyKeyIsReusedForADifferentAsset() throws Exception {
        when(proposalAssetUseCase.registerAsset(any(RegisterProposalAssetUseCase.RegisterCommand.class)))
                .thenThrow(new ProposalAssetConflictException(
                        "Idempotency-Key was already used to register a different proposal asset"));

        mockMvc.perform(post("/internal/agent-tools/v1/crm/quotes/{quoteId}/assets", QUOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "demo-tenant")
                        .header("X-Agent-Id", "vextis_crm_agent")
                        .header("X-Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idemp-key-proposal-asset-004")
                        .content("""
                                {
                                  "storageUri": "%s",
                                  "mediaType": "IMAGE",
                                  "modelId": "imagen-3.0-generate-002",
                                  "promptSummary": "A different render entirely",
                                  "aiLabel": "AI-Generated"
                                }
                                """.formatted(STORAGE_URI)))
                .andExpect(status().isConflict());
    }
}
