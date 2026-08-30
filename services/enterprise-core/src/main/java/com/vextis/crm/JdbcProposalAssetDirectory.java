package com.vextis.crm;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcProposalAssetDirectory implements ProposalAssetDirectory {

    private static final RowMapper<ProposalAssetView> ROW_MAPPER = new ProposalAssetRowMapper();

    private final NamedParameterJdbcTemplate jdbc;
    private final java.time.Clock clock;

    JdbcProposalAssetDirectory(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, java.time.Clock.systemUTC());
    }

    JdbcProposalAssetDirectory(NamedParameterJdbcTemplate jdbc, java.time.Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static String computeFingerprint(String quoteId, String promptSummary) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((quoteId + ":" + (promptSummary != null ? promptSummary.trim() : "")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }

    @Override
    public List<ProposalAssetView> findByQuoteId(String tenantId, String quoteId) {
        return findByQuoteId(tenantId, quoteId, 20);
    }

    @Override
    public List<ProposalAssetView> findByQuoteId(String tenantId, String quoteId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, storage_generation, content_type, content_hash, size_bytes,
                       media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND quote_id = :quoteId
                ORDER BY created_at DESC
                LIMIT :limit
                """,
                Map.of("tenantId", tenantId, "quoteId", quoteId, "limit", boundedLimit),
                ROW_MAPPER
        );
    }

    @Override
    public List<ProposalAssetView> findAll(String tenantId) {
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, storage_generation, content_type, content_hash, size_bytes,
                       media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId
                ORDER BY created_at DESC
                """,
                Map.of("tenantId", tenantId),
                ROW_MAPPER
        );
    }

    @Override
    public Optional<ProposalAssetView> findById(String tenantId, UUID assetId) {
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, storage_generation, content_type, content_hash, size_bytes,
                       media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND id = :assetId
                """,
                Map.of("tenantId", tenantId, "assetId", assetId),
                ROW_MAPPER
        ).stream().findFirst();
    }

    @Override
    public Optional<ProposalAssetView> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return jdbc.query(
                """
                SELECT id, quote_id, storage_uri, storage_generation, content_type, content_hash, size_bytes,
                       media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of("tenantId", tenantId, "idempotencyKey", idempotencyKey),
                ROW_MAPPER
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public ReservationResult reserve(
            String tenantId,
            String quoteId,
            String idempotencyKey,
            String fingerprint,
            String ownerAgentId
    ) {
        UUID newReservationId = UUID.randomUUID();
        Instant now = clock.instant();
        OffsetDateTime offsetNow = OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);

        Map<String, Object> params = new HashMap<>();
        params.put("id", newReservationId);
        params.put("tenantId", tenantId);
        params.put("quoteId", quoteId);
        params.put("idempotencyKey", idempotencyKey);
        params.put("fingerprint", fingerprint);
        params.put("status", "PENDING");
        params.put("ownerAgentId", ownerAgentId);
        params.put("createdAt", offsetNow);
        params.put("updatedAt", offsetNow);

        int inserted = jdbc.update(
                """
                INSERT INTO proposal_asset_reservations (
                    id, tenant_id, quote_id, idempotency_key, fingerprint, status, owner_agent_id, created_at, updated_at
                ) VALUES (
                    :id, :tenantId, :quoteId, :idempotencyKey, :fingerprint, :status, :ownerAgentId, :createdAt, :updatedAt
                )
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """,
                params
        );

        if (inserted > 0) {
            return new ReservationResult(ReservationStatus.RESERVED, true, fingerprint, Optional.empty());
        }

        // Existing reservation found
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT fingerprint, status, owner_agent_id, asset_id
                FROM proposal_asset_reservations
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of("tenantId", tenantId, "idempotencyKey", idempotencyKey)
        );

        if (rows.isEmpty()) {
            Optional<ProposalAssetView> existingAsset = findByIdempotencyKey(tenantId, idempotencyKey);
            if (existingAsset.isPresent()) {
                return new ReservationResult(ReservationStatus.COMPLETED, false, fingerprint, existingAsset);
            }
            throw new IllegalStateException("Reservation row not found following conflict");
        }

        Map<String, Object> row = rows.get(0);
        String existingFingerprint = (String) row.get("fingerprint");
        String existingStatus = (String) row.get("status");
        String existingOwner = (String) row.get("owner_agent_id");
        UUID existingAssetId = (UUID) row.get("asset_id");

        if (!Objects.equals(existingFingerprint, fingerprint)) {
            throw new ProposalAssetConflictException(
                    "Idempotency key '%s' was already reserved with a different payload fingerprint".formatted(idempotencyKey)
            );
        }

        if ("COMPLETED".equals(existingStatus)) {
            Optional<ProposalAssetView> assetOpt = existingAssetId != null
                    ? findById(tenantId, existingAssetId)
                    : findByIdempotencyKey(tenantId, idempotencyKey);
            return new ReservationResult(ReservationStatus.COMPLETED, false, fingerprint, assetOpt);
        }

        boolean isOwner = Objects.equals(existingOwner, ownerAgentId);
        return new ReservationResult(ReservationStatus.PENDING, isOwner, fingerprint, Optional.empty());
    }

    @Override
    @Transactional
    public RegisterProposalAssetResult registerAsset(RegisterProposalAssetCommand command) {
        UUID newId = UUID.randomUUID();
        Instant now = clock.instant();
        OffsetDateTime offsetNow = OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);

        String fingerprint = computeFingerprint(command.quoteId(), command.promptSummary());

        // Validate any pre-existing reservation for this idempotency key
        List<Map<String, Object>> existingReservations = jdbc.queryForList(
                """
                SELECT fingerprint, status, owner_agent_id, asset_id
                FROM proposal_asset_reservations
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of("tenantId", command.tenantId(), "idempotencyKey", command.idempotencyKey())
        );
        if (!existingReservations.isEmpty()) {
            String existingFingerprint = (String) existingReservations.get(0).get("fingerprint");
            if (!Objects.equals(existingFingerprint, fingerprint)) {
                throw new ProposalAssetConflictException(
                        "Idempotency-Key was already reserved with a different payload fingerprint"
                );
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("id", newId);
        params.put("tenantId", command.tenantId());
        params.put("quoteId", command.quoteId());
        params.put("storageUri", command.storageUri());
        params.put("storageGeneration", command.storageGeneration());
        params.put("contentType", command.contentType());
        params.put("contentHash", command.contentHash());
        params.put("sizeBytes", command.sizeBytes());
        params.put("mediaType", command.mediaType().name());
        params.put("modelId", command.modelId());
        params.put("promptSummary", command.promptSummary());
        params.put("aiLabel", command.aiLabel());
        params.put("actorType", command.actorType());
        params.put("actorId", command.actorId());
        params.put("correlationId", command.correlationId());
        params.put("idempotencyKey", command.idempotencyKey());
        params.put("createdAt", offsetNow);

        int inserted = jdbc.update(
                """
                INSERT INTO proposal_assets (
                    id, tenant_id, quote_id, storage_uri, storage_generation, content_type, content_hash, size_bytes,
                    media_type, model_id, prompt_summary, ai_label,
                    created_by_actor_type, created_by_actor_id, correlation_id, idempotency_key, created_at
                ) VALUES (
                    :id, :tenantId, :quoteId, :storageUri, :storageGeneration, :contentType, :contentHash, :sizeBytes,
                    :mediaType, :modelId, :promptSummary, :aiLabel,
                    :actorType, :actorId, :correlationId, :idempotencyKey, :createdAt
                )
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """,
                params
        );

        if (inserted > 0) {
            // Upsert reservation status to COMPLETED
            jdbc.update(
                    """
                    INSERT INTO proposal_asset_reservations (
                        id, tenant_id, quote_id, idempotency_key, fingerprint, status, owner_agent_id, asset_id, created_at, updated_at
                    ) VALUES (
                        :resId, :tenantId, :quoteId, :idempotencyKey, :fingerprint, 'COMPLETED', :actorId, :assetId, :createdAt, :createdAt
                    )
                    ON CONFLICT (tenant_id, idempotency_key) DO UPDATE
                    SET status = 'COMPLETED', asset_id = :assetId, updated_at = :createdAt
                    """,
                    Map.of(
                            "resId", UUID.randomUUID(),
                            "tenantId", command.tenantId(),
                            "quoteId", command.quoteId(),
                            "idempotencyKey", command.idempotencyKey(),
                            "fingerprint", fingerprint,
                            "actorId", command.actorId(),
                            "assetId", newId,
                            "createdAt", offsetNow
                    )
            );

            ProposalAssetView createdView = new ProposalAssetView(
                    newId, command.quoteId(), command.storageUri(), command.storageGeneration(),
                    command.contentType(), command.contentHash(), command.sizeBytes(),
                    command.mediaType(), command.modelId(),
                    command.promptSummary(), command.aiLabel(), command.actorType(), command.actorId(),
                    command.correlationId(), now
            );
            return new RegisterProposalAssetResult(createdView, true);
        }

        ProposalAssetView existing = jdbc.query(
                """
                SELECT id, quote_id, storage_uri, storage_generation, content_type, content_hash, size_bytes,
                       media_type, model_id, prompt_summary, ai_label,
                       created_by_actor_type, created_by_actor_id, correlation_id, created_at
                FROM proposal_assets
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of("tenantId", command.tenantId(), "idempotencyKey", command.idempotencyKey()),
                ROW_MAPPER
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("Failed to find idempotent proposal asset"));

        // The idempotency key alone does not uniquely constrain the payload:
        // replaying it with a different quote or content must be rejected as
        // a conflict rather than silently handing back the unrelated asset
        // that happened to be registered first under this key.
        if (!existing.quoteId().equals(command.quoteId())
                || !existing.storageUri().equals(command.storageUri())
                || existing.mediaType() != command.mediaType()
                || !existing.modelId().equals(command.modelId())
                || !existing.promptSummary().equals(command.promptSummary())
                || !existing.aiLabel().equals(command.aiLabel())
                || (command.storageGeneration() != null && existing.storageGeneration() != null
                    && !Objects.equals(existing.storageGeneration(), command.storageGeneration()))
                || (command.contentHash() != null && existing.contentHash() != null
                    && !Objects.equals(existing.contentHash(), command.contentHash()))) {
            throw new ProposalAssetConflictException(
                    "Idempotency-Key was already used to register a different proposal asset");
        }

        return new RegisterProposalAssetResult(existing, false);
    }

    private static final class ProposalAssetRowMapper implements RowMapper<ProposalAssetView> {
        @Override
        public ProposalAssetView mapRow(ResultSet rs, int rowNum) throws SQLException {
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            Long storageGen = rs.getObject("storage_generation") != null ? rs.getLong("storage_generation") : null;
            Long size = rs.getObject("size_bytes") != null ? rs.getLong("size_bytes") : null;
            return new ProposalAssetView(
                    rs.getObject("id", UUID.class),
                    rs.getString("quote_id"),
                    rs.getString("storage_uri"),
                    storageGen,
                    rs.getString("content_type"),
                    rs.getString("content_hash"),
                    size,
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
