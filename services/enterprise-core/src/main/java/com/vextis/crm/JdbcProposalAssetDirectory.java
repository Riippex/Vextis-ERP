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
            String fingerprint
    ) {
        UUID newReservationId = UUID.randomUUID();
        String newReservationToken = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(300);
        OffsetDateTime offsetNow = OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);
        OffsetDateTime offsetExpiresAt = OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC);

        Map<String, Object> params = new HashMap<>();
        params.put("id", newReservationId);
        params.put("tenantId", tenantId);
        params.put("quoteId", quoteId);
        params.put("idempotencyKey", idempotencyKey);
        params.put("fingerprint", fingerprint);
        params.put("status", "PENDING");
        params.put("reservationToken", newReservationToken);
        params.put("expiresAt", offsetExpiresAt);
        params.put("createdAt", offsetNow);
        params.put("updatedAt", offsetNow);

        int inserted = jdbc.update(
                """
                INSERT INTO proposal_asset_reservations (
                    id, tenant_id, quote_id, idempotency_key, fingerprint, status, reservation_token, expires_at, created_at, updated_at
                ) VALUES (
                    :id, :tenantId, :quoteId, :idempotencyKey, :fingerprint, :status, :reservationToken, :expiresAt, :createdAt, :updatedAt
                )
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """,
                params
        );

        if (inserted > 0) {
            return new ReservationResult(ReservationStatus.RESERVED, true, newReservationToken, fingerprint, Optional.empty());
        }

        // Existing reservation found
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT fingerprint, status, reservation_token, expires_at, asset_id
                FROM proposal_asset_reservations
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of("tenantId", tenantId, "idempotencyKey", idempotencyKey)
        );

        if (rows.isEmpty()) {
            Optional<ProposalAssetView> existingAsset = findByIdempotencyKey(tenantId, idempotencyKey);
            if (existingAsset.isPresent()) {
                return new ReservationResult(ReservationStatus.COMPLETED, false, null, fingerprint, existingAsset);
            }
            throw new IllegalStateException("Reservation row not found following conflict");
        }

        Map<String, Object> row = rows.get(0);
        String existingFingerprint = (String) row.get("fingerprint");
        String existingStatus = (String) row.get("status");
        OffsetDateTime rowExpiresAt = (OffsetDateTime) row.get("expires_at");
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
            return new ReservationResult(ReservationStatus.COMPLETED, false, null, fingerprint, assetOpt);
        }

        // PENDING: check lease expiration for takeover
        Instant rowExpiresInstant = rowExpiresAt != null ? rowExpiresAt.toInstant() : Instant.EPOCH;
        if (rowExpiresInstant.isBefore(now)) {
            String takeoverToken = UUID.randomUUID().toString();
            OffsetDateTime newExpiresAt = OffsetDateTime.ofInstant(now.plusSeconds(300), java.time.ZoneOffset.UTC);
            int updated = jdbc.update(
                    """
                    UPDATE proposal_asset_reservations
                    SET reservation_token = :newToken, expires_at = :newExpiry, updated_at = :now
                    WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                      AND status = 'PENDING' AND expires_at = :oldExpiry
                    """,
                    Map.of(
                            "newToken", takeoverToken,
                            "newExpiry", newExpiresAt,
                            "now", offsetNow,
                            "tenantId", tenantId,
                            "idempotencyKey", idempotencyKey,
                            "oldExpiry", rowExpiresAt
                    )
            );
            if (updated > 0) {
                return new ReservationResult(ReservationStatus.RESERVED, true, takeoverToken, fingerprint, Optional.empty());
            }
        }

        return new ReservationResult(ReservationStatus.PENDING, false, null, fingerprint, Optional.empty());
    }

    @Override
    @Transactional
    public RegisterProposalAssetResult registerAsset(RegisterProposalAssetCommand command) {
        Instant now = clock.instant();
        OffsetDateTime offsetNow = OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);
        String fingerprint = computeFingerprint(command.quoteId(), command.promptSummary());

        // 1. Check for existing asset replay (idempotency)
        Optional<ProposalAssetView> existingOpt = findByIdempotencyKey(command.tenantId(), command.idempotencyKey());
        if (existingOpt.isPresent()) {
            ProposalAssetView existing = existingOpt.get();
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

        // 2. Reject registrations without reservation token
        if (command.reservationToken() == null || command.reservationToken().isBlank()) {
            throw new ProposalAssetConflictException(
                    "Missing reservation token: registration requires a valid reservation token");
        }

        // 3. Atomically claim/consume the pending reservation
        int claimed = jdbc.update(
                """
                UPDATE proposal_asset_reservations
                SET status = 'COMPLETING', updated_at = :now
                WHERE tenant_id = :tenantId
                  AND idempotency_key = :idempotencyKey
                  AND reservation_token = :reservationToken
                  AND status = 'PENDING'
                  AND expires_at >= :now
                  AND fingerprint = :fingerprint
                """,
                Map.of(
                        "tenantId", command.tenantId(),
                        "idempotencyKey", command.idempotencyKey(),
                        "reservationToken", command.reservationToken(),
                        "fingerprint", fingerprint,
                        "now", offsetNow
                )
        );

        if (claimed != 1) {
            // Check if concurrent registration completed
            Optional<ProposalAssetView> concurrentExisting = findByIdempotencyKey(command.tenantId(), command.idempotencyKey());
            if (concurrentExisting.isPresent()) {
                ProposalAssetView existing = concurrentExisting.get();
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

            // Inspect reservation state to return precise conflict explanation
            List<Map<String, Object>> resRows = jdbc.queryForList(
                    """
                    SELECT fingerprint, status, reservation_token, expires_at
                    FROM proposal_asset_reservations
                    WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                    """,
                    Map.of("tenantId", command.tenantId(), "idempotencyKey", command.idempotencyKey())
            );

            if (resRows.isEmpty()) {
                throw new ProposalAssetConflictException(
                        "No active reservation found for idempotency key: registration requires prior reservation");
            }

            Map<String, Object> resRow = resRows.get(0);
            String existingFingerprint = (String) resRow.get("fingerprint");
            String existingStatus = (String) resRow.get("status");
            String existingToken = (String) resRow.get("reservation_token");
            OffsetDateTime resExpiresAt = (OffsetDateTime) resRow.get("expires_at");

            if (!Objects.equals(existingFingerprint, fingerprint)) {
                throw new ProposalAssetConflictException(
                        "Idempotency-Key was already reserved with a different payload fingerprint");
            }
            if (!Objects.equals(existingToken, command.reservationToken())) {
                throw new ProposalAssetConflictException(
                        "Invalid or mismatched reservation token for idempotency key");
            }
            if (resExpiresAt != null && resExpiresAt.toInstant().isBefore(now)) {
                throw new ProposalAssetConflictException(
                        "Reservation lease expired before asset could be registered");
            }
            throw new ProposalAssetConflictException(
                    "Reservation is not in PENDING state or was already claimed (status: " + existingStatus + ")");
        }

        // 4. Insert newly created asset
        UUID newId = UUID.randomUUID();
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

        jdbc.update(
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
                """,
                params
        );

        jdbc.update(
                """
                UPDATE proposal_asset_reservations
                SET status = 'COMPLETED', asset_id = :assetId, updated_at = :now
                WHERE tenant_id = :tenantId AND idempotency_key = :idempotencyKey
                """,
                Map.of(
                        "tenantId", command.tenantId(),
                        "idempotencyKey", command.idempotencyKey(),
                        "assetId", newId,
                        "now", offsetNow
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
