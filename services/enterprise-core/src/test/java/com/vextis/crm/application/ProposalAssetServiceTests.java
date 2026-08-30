package com.vextis.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vextis.audit.AuditTrail;
import com.vextis.crm.GcsProposalAssetStorage;
import com.vextis.crm.ProposalAssetDirectory;
import com.vextis.crm.QuoteExecutionLookup;
import com.vextis.crm.RegisterProposalAssetUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProposalAssetServiceTests {

    private static final UUID QUOTE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID ASSET_ID = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
    private static final String STORAGE_URI = "gs://vextis-assets/proposals/deadbeef/chair.png";
    private static final Instant NOW = Instant.parse("2026-08-28T16:00:00Z");

    private ProposalAssetDirectory proposalAssets;
    private QuoteExecutionLookup quoteLookup;
    private GcsProposalAssetStorage assetStorage;
    private AuditTrail audit;
    private NamedParameterJdbcTemplate jdbc;
    private ObjectMapper objectMapper;
    private ProposalAssetService service;

    @BeforeEach
    void setUp() {
        proposalAssets = mock(ProposalAssetDirectory.class);
        quoteLookup = mock(QuoteExecutionLookup.class);
        assetStorage = mock(GcsProposalAssetStorage.class);
        audit = mock(AuditTrail.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        objectMapper = new ObjectMapper();
        service = new ProposalAssetService(
                proposalAssets,
                quoteLookup,
                assetStorage,
                audit,
                jdbc,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static QuoteExecutionLookup.QuoteExecution quote(String correlationId) {
        return new QuoteExecutionLookup.QuoteExecution(QUOTE_ID, "demo-tenant", correlationId);
    }

    @Test
    void preflightSucceedsWhenQuoteExistsAndCorrelationMatches() {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.of(quote("corr-auth-123")));

        RegisterProposalAssetUseCase.PreflightResult result = service.preflight(
                new RegisterProposalAssetUseCase.PreflightCommand("demo-tenant", "vextis_crm_agent", QUOTE_ID, "corr-auth-123"));

        assertThat(result.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(result.authorized()).isTrue();
        assertThat(result.correlationId()).isEqualTo("corr-auth-123");
    }

    @Test
    void preflightFailsWhenQuoteDoesNotExist() {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preflight(
                new RegisterProposalAssetUseCase.PreflightCommand("demo-tenant", "vextis_crm_agent", QUOTE_ID, "corr-auth-123")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No quote or order found");
    }

    @Test
    void preflightFailsWhenCorrelationMismatchesAuthoritativeExecution() {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.of(quote("corr-auth-123")));

        assertThatThrownBy(() -> service.preflight(
                new RegisterProposalAssetUseCase.PreflightCommand("demo-tenant", "vextis_crm_agent", QUOTE_ID, "corr-fake-999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Correlation ID mismatch");
    }

    @Test
    void registerAssetOrchestratesStorageValidationPersistenceAuditAndOutbox() {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.of(quote("corr-auth-123")));
        when(assetStorage.assertUploaded("demo-tenant", STORAGE_URI, ProposalAssetDirectory.MediaType.IMAGE))
                .thenReturn(new GcsProposalAssetStorage.AssetObjectMetadata(101L, "image/png", "hash123", 4096L));

        when(proposalAssets.registerAsset(any(ProposalAssetDirectory.RegisterProposalAssetCommand.class)))
                .thenReturn(new ProposalAssetDirectory.RegisterProposalAssetResult(
                        new ProposalAssetDirectory.ProposalAssetView(
                                ASSET_ID,
                                QUOTE_ID.toString(),
                                STORAGE_URI,
                                101L,
                                "image/png",
                                "hash123",
                                4096L,
                                ProposalAssetDirectory.MediaType.IMAGE,
                                "imagen-3.0-generate-002",
                                "Ergonomic chair concept",
                                "AI-Generated Proposal Concept",
                                "AGENT",
                                "vextis_crm_agent",
                                "corr-auth-123",
                                NOW
                        ),
                        true
                ));

        ProposalAssetDirectory.ProposalAssetView result = service.registerAsset(
                new RegisterProposalAssetUseCase.RegisterCommand(
                        "demo-tenant",
                        "vextis_crm_agent",
                        QUOTE_ID,
                        "corr-auth-123",
                        "idemp-001",
                        STORAGE_URI,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Ergonomic chair concept",
                        "AI-Generated Proposal Concept"
                )
        );

        assertThat(result.id()).isEqualTo(ASSET_ID);
        assertThat(result.storageGeneration()).isEqualTo(101L);

        // Verify storage validation was called
        verify(assetStorage).assertUploaded("demo-tenant", STORAGE_URI, ProposalAssetDirectory.MediaType.IMAGE);

        // Verify directory was called with metadata
        verify(proposalAssets).registerAsset(argThat(cmd ->
                cmd.storageGeneration().equals(101L)
                        && cmd.contentType().equals("image/png")
                        && cmd.correlationId().equals("corr-auth-123")));

        // Verify audit trail recorded with SUCCEEDED and authoritative correlationId
        verify(audit).recordAgentDecision(argThat(d ->
                d.agentId().equals("vextis_crm_agent")
                        && d.action().equals("crm.proposal-asset.registered")
                        && d.correlationId().equals("corr-auth-123")
                        && d.result() == AuditTrail.AgentDecisionResult.SUCCEEDED));

        // Verify outbox event insertion
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO outbox_events")), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }

    @Test
    void registerAssetOnIdempotentReplayReturnsAssetWithoutEmittingAuditOrOutbox() {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.of(quote("corr-auth-123")));
        when(assetStorage.assertUploaded("demo-tenant", STORAGE_URI, ProposalAssetDirectory.MediaType.IMAGE))
                .thenReturn(new GcsProposalAssetStorage.AssetObjectMetadata(101L, "image/png", "hash123", 4096L));

        when(proposalAssets.registerAsset(any(ProposalAssetDirectory.RegisterProposalAssetCommand.class)))
                .thenReturn(new ProposalAssetDirectory.RegisterProposalAssetResult(
                        new ProposalAssetDirectory.ProposalAssetView(
                                ASSET_ID,
                                QUOTE_ID.toString(),
                                STORAGE_URI,
                                101L,
                                "image/png",
                                "hash123",
                                4096L,
                                ProposalAssetDirectory.MediaType.IMAGE,
                                "imagen-3.0-generate-002",
                                "Ergonomic chair concept",
                                "AI-Generated Proposal Concept",
                                "AGENT",
                                "vextis_crm_agent",
                                "corr-auth-123",
                                NOW
                        ),
                        false
                ));

        ProposalAssetDirectory.ProposalAssetView result = service.registerAsset(
                new RegisterProposalAssetUseCase.RegisterCommand(
                        "demo-tenant",
                        "vextis_crm_agent",
                        QUOTE_ID,
                        "corr-auth-123",
                        "idemp-001",
                        STORAGE_URI,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Ergonomic chair concept",
                        "AI-Generated Proposal Concept"
                )
        );

        assertThat(result.id()).isEqualTo(ASSET_ID);

        // Audit and outbox must NEVER be called on replay (created == false)
        verifyNoInteractions(audit);
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }

    @Test
    void registerAssetRejectsCorrelationMismatchBeforeStorageOrPersistence() {
        when(quoteLookup.findQuote("demo-tenant", QUOTE_ID)).thenReturn(Optional.of(quote("corr-auth-123")));

        assertThatThrownBy(() -> service.registerAsset(
                new RegisterProposalAssetUseCase.RegisterCommand(
                        "demo-tenant",
                        "vextis_crm_agent",
                        QUOTE_ID,
                        "corr-mismatch-header",
                        "idemp-001",
                        STORAGE_URI,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Ergonomic chair concept",
                        "AI-Generated Proposal Concept"
                )
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Correlation ID mismatch");

        verifyNoInteractions(assetStorage, proposalAssets, audit, jdbc);
    }
}
