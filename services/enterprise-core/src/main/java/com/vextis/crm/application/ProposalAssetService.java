package com.vextis.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vextis.audit.AuditTrail;
import com.vextis.crm.GcsProposalAssetStorage;
import com.vextis.crm.ProposalAssetDirectory;
import com.vextis.crm.QuoteExecutionLookup;
import com.vextis.crm.RegisterProposalAssetUseCase;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProposalAssetService implements RegisterProposalAssetUseCase {

    private final ProposalAssetDirectory proposalAssets;
    private final QuoteExecutionLookup quoteLookup;
    private final GcsProposalAssetStorage assetStorage;
    private final AuditTrail audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProposalAssetService(
            ProposalAssetDirectory proposalAssets,
            QuoteExecutionLookup quoteLookup,
            GcsProposalAssetStorage assetStorage,
            AuditTrail audit,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.proposalAssets = proposalAssets;
        this.quoteLookup = quoteLookup;
        this.assetStorage = assetStorage;
        this.audit = audit;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PreflightResult preflight(PreflightCommand command) {
        QuoteExecutionLookup.QuoteExecution quote = quoteLookup.findQuote(command.tenantId(), command.quoteId())
                .orElseThrow(() -> new NoSuchElementException("No quote or order found for this tenant"));

        if (!quote.correlationId().equals(command.correlationId())) {
            throw new IllegalArgumentException(
                    "Correlation ID mismatch: request header does not match authoritative execution correlation ID");
        }

        String tenantPrefix = GcsProposalAssetStorage.objectPrefix(command.tenantId());

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<ProposalAssetDirectory.ProposalAssetView> existing = proposalAssets.findByIdempotencyKey(
                    command.tenantId(), command.idempotencyKey());
            if (existing.isPresent()) {
                ProposalAssetDirectory.ProposalAssetView view = existing.get();
                if (view.quoteId().equals(command.quoteId().toString())
                        && (command.promptSummary() == null || view.promptSummary().equals(command.promptSummary()))) {
                    return new PreflightResult(command.quoteId(), tenantPrefix, quote.correlationId(), true, true, view);
                }
            }
        }

        return new PreflightResult(command.quoteId(), tenantPrefix, quote.correlationId(), true, false, null);
    }

    @Override
    @Transactional
    public ProposalAssetDirectory.ProposalAssetView registerAsset(RegisterCommand command) {
        QuoteExecutionLookup.QuoteExecution quote = quoteLookup.findQuote(command.tenantId(), command.quoteId())
                .orElseThrow(() -> new NoSuchElementException("No quote or order found for this tenant"));

        if (!quote.correlationId().equals(command.correlationId())) {
            throw new IllegalArgumentException(
                    "Correlation ID mismatch: request header does not match authoritative execution correlation ID");
        }

        // Validate that storage object exists, size/content constraints are satisfied, and extract metadata
        GcsProposalAssetStorage.AssetObjectMetadata metadata = assetStorage.assertUploaded(
                command.tenantId(), command.storageUri(), command.mediaType());

        // Always persist with the authoritative execution correlation ID
        String authoritativeCorrelationId = quote.correlationId();

        // Register asset (idempotent)
        ProposalAssetDirectory.RegisterProposalAssetResult result = proposalAssets.registerAsset(
                new ProposalAssetDirectory.RegisterProposalAssetCommand(
                        command.tenantId(),
                        command.quoteId().toString(),
                        command.storageUri(),
                        metadata.generation(),
                        metadata.contentType(),
                        metadata.contentHash(),
                        metadata.sizeBytes(),
                        command.mediaType(),
                        command.modelId(),
                        command.promptSummary(),
                        command.aiLabel(),
                        "AGENT",
                        command.agentId(),
                        authoritativeCorrelationId,
                        command.idempotencyKey()
                )
        );

        ProposalAssetDirectory.ProposalAssetView asset = result.view();

        // Only record audit trail and outbox event when the asset is newly CREATED, never on replayed idempotent registrations
        if (result.created()) {
            Instant now = clock.instant();

            // 1. Audit trail record
            audit.recordAgentDecision(new AuditTrail.AgentDecision(
                    command.tenantId(),
                    authoritativeCorrelationId,
                    command.agentId(),
                    "crm.proposal-asset.registered",
                    "PROPOSAL_ASSET",
                    asset.id(),
                    AuditTrail.AgentDecisionResult.SUCCEEDED,
                    now
            ));

            // 2. Outbox event: quote.visual.generated (version 1)
            UUID eventId = UUID.randomUUID();
            Map<String, Object> payload = Map.ofEntries(
                    Map.entry("assetId", asset.id().toString()),
                    Map.entry("quoteId", asset.quoteId()),
                    Map.entry("storageUri", asset.storageUri()),
                    Map.entry("storageGeneration", asset.storageGeneration() != null ? asset.storageGeneration() : 0L),
                    Map.entry("mediaType", asset.mediaType().name()),
                    Map.entry("modelId", asset.modelId()),
                    Map.entry("promptSummary", asset.promptSummary()),
                    Map.entry("aiLabel", asset.aiLabel()),
                    Map.entry("agentId", command.agentId()),
                    Map.entry("correlationId", authoritativeCorrelationId),
                    Map.entry("createdAt", asset.createdAt().toString())
            );

            Map<String, Object> envelope = Map.of(
                    "event_id", eventId.toString(),
                    "event_type", "quote.visual.generated",
                    "event_version", 1,
                    "occurred_at", now.toString(),
                    "producer", "enterprise-core",
                    "tenant_id", command.tenantId(),
                    "correlation_id", authoritativeCorrelationId,
                    "causation_id", asset.id().toString(),
                    "actor", Map.of("type", "AGENT", "id", command.agentId()),
                    "payload", payload
            );

            try {
                String envelopeJson = objectMapper.writeValueAsString(envelope);
                jdbc.update(
                        """
                        INSERT INTO outbox_events
                            (event_id, event_type, event_version, aggregate_type, aggregate_id, tenant_id,
                             correlation_id, causation_id, payload, occurred_at)
                        VALUES (:eventId, 'quote.visual.generated', 1, 'PROPOSAL_ASSET',
                                :aggregateId, :tenantId, :correlationId, :causationId, CAST(:payload AS JSONB), :occurredAt)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                        new MapSqlParameterSource()
                                .addValue("eventId", eventId)
                                .addValue("aggregateId", asset.id().toString())
                                .addValue("tenantId", command.tenantId())
                                .addValue("correlationId", authoritativeCorrelationId)
                                .addValue("causationId", asset.id().toString())
                                .addValue("payload", envelopeJson)
                                .addValue("occurredAt", now, Types.TIMESTAMP_WITH_TIMEZONE)
                );
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to serialize outbox event payload", exception);
            }
        }

        return asset;
    }
}
