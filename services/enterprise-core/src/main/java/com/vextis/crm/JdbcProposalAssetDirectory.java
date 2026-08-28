package com.vextis.crm;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcProposalAssetDirectory implements ProposalAssetDirectory {

    private static final RowMapper<ProposalAssetView> ROW_MAPPER = new ProposalAssetRowMapper();

    private final NamedParameterJdbcTemplate jdbc;

    JdbcProposalAssetDirectory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProposalAssetView> findByQuoteId(String tenantId, String quoteId) {
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND quote_id = :quoteId
                ORDER BY created_at DESC
                """,
                Map.of("tenantId", tenantId, "quoteId", quoteId),
                ROW_MAPPER
        );
    }

    @Override
    public List<ProposalAssetView> findAll(String tenantId) {
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId
                ORDER BY created_at DESC
                LIMIT 100
                """,
                Map.of("tenantId", tenantId),
                ROW_MAPPER
        );
    }

    @Override
    public Optional<ProposalAssetView> findById(String tenantId, UUID assetId) {
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND id = :assetId
                """,
                Map.of("tenantId", tenantId, "assetId", assetId),
                ROW_MAPPER
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public ProposalAssetView registerAsset(RegisterProposalAssetCommand command) {
        UUID newId = UUID.randomUUID();
        Instant now = Instant.now();

        int inserted = jdbc.update(
                """
                INSERT INTO proposal_assets (
                    id, tenant_id, quote_id, storage_uri, media_type, model_id, prompt_summary, ai_label,
                    created_by_actor_type, created_by_actor_id, correlation_id, idempotency_key, created_at
                ) VALUES (
                    :id, :tenantId, :quoteId, :storageUri, :mediaType, :modelId, :promptSummary, :aiLabel,
                    :actorType, :actorId, :correlationId, :idempotencyKey, :createdAt
                )
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """,
                Map.ofEntries(
                        Map.entry("id", newId),
                        Map.entry("tenantId", command.tenantId()),
                        Map.entry("quoteId", command.quoteId()),
                        Map.entry("storageUri", command.storageUri()),
                        Map.entry("mediaType", command.mediaType().name()),
                        Map.entry("modelId", command.modelId()),
                        Map.entry("promptSummary", command.promptSummary()),
                        Map.entry("aiLabel", command.aiLabel()),
                        Map.entry("actorType", command.actorType()),
                        Map.entry("actorId", command.actorId()),
                        Map.entry("correlationId", command.correlationId()),
                        Map.entry("idempotencyKey", command.idempotencyKey()),
                        Map.entry("createdAt", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                )
        );

        if (inserted > 0) {
            return new ProposalAssetView(
                    newId, command.quoteId(), command.storageUri(), command.mediaType(), command.modelId(),
                    command.promptSummary(), command.aiLabel(), command.actorType(), command.actorId(),
                    command.correlationId(), now
            );
        }

        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of("tenantId", command.tenantId(), "idempotencyKey", command.idempotencyKey()),
                ROW_MAPPER
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("Failed to find idempotent proposal asset"));
    }

    private static final class ProposalAssetRowMapper implements RowMapper<ProposalAssetView> {
        @Override
        public ProposalAssetView mapRow(ResultSet rs, int rowNum) throws SQLException {
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            return new ProposalAssetView(
                    rs.getObject("id", UUID.class),
                    rs.getString("quote_id"),
                    rs.getString("storage_uri"),
                    MediaType.valueOf(rs.getString("media_type")),
                    rs.getString("model_id"),
                    rs.getString("prompt_summary"),
                    rs.getString("ai_label"),
                    rs.getString("created_by_actor_type"),
                    rs.getString("created_by_actor_id"),
                    rs.getString("correlation_id"),
                    createdAt != null ? createdAt.toInstant() : Instant.now()
            );
        }
    }
}
