package com.vextis.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcProposalAssetDirectoryTests {

    private static final UUID ASSET_ID = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
    private static final Instant NOW = Instant.parse("2026-08-28T16:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcProposalAssetDirectory directory;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        directory = new JdbcProposalAssetDirectory(jdbc);
    }

    @Test
    void registerAssetInsertsNewRecordWhenNoConflict() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        ProposalAssetDirectory.RegisterProposalAssetResult result = directory.registerAsset(
                new ProposalAssetDirectory.RegisterProposalAssetCommand(
                        "demo-tenant",
                        "quote-001",
                        "gs://bucket/proposals/x/quote-001.png",
                        42L,
                        "image/png",
                        "hash123",
                        1024L,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Chair visual",
                        "AI-Generated",
                        "AGENT",
                        "vextis_crm_agent",
                        "corr-001",
                        "idemp-001"
                )
        );

        assertThat(result.created()).isTrue();
        assertThat(result.view().quoteId()).isEqualTo("quote-001");
        assertThat(result.view().storageGeneration()).isEqualTo(42L);
        assertThat(result.view().contentType()).isEqualTo("image/png");
        assertThat(result.view().contentHash()).isEqualTo("hash123");
        assertThat(result.view().sizeBytes()).isEqualTo(1024L);
        assertThat(result.view().mediaType()).isEqualTo(ProposalAssetDirectory.MediaType.IMAGE);
    }

    @Test
    void registerAssetReturnsExistingRecordOnExactIdempotentReplay() {
        // Insert returned 0 because ON CONFLICT DO NOTHING
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);

        ProposalAssetDirectory.ProposalAssetView existing = new ProposalAssetDirectory.ProposalAssetView(
                ASSET_ID,
                "quote-001",
                "gs://bucket/proposals/x/quote-001.png",
                42L,
                "image/png",
                "hash123",
                1024L,
                ProposalAssetDirectory.MediaType.IMAGE,
                "imagen-3.0-generate-002",
                "Chair visual",
                "AI-Generated",
                "AGENT",
                "vextis_crm_agent",
                "corr-001",
                NOW
        );

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(existing));

        ProposalAssetDirectory.RegisterProposalAssetResult result = directory.registerAsset(
                new ProposalAssetDirectory.RegisterProposalAssetCommand(
                        "demo-tenant",
                        "quote-001",
                        "gs://bucket/proposals/x/quote-001.png",
                        42L,
                        "image/png",
                        "hash123",
                        1024L,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Chair visual",
                        "AI-Generated",
                        "AGENT",
                        "vextis_crm_agent",
                        "corr-001",
                        "idemp-001"
                )
        );

        assertThat(result.created()).isFalse();
        assertThat(result.view().id()).isEqualTo(ASSET_ID);
        assertThat(result.view().quoteId()).isEqualTo("quote-001");
    }

    @Test
    void findByIdempotencyKeyReturnsAssetWhenFound() {
        ProposalAssetDirectory.ProposalAssetView existing = new ProposalAssetDirectory.ProposalAssetView(
                ASSET_ID,
                "quote-001",
                "gs://bucket/proposals/x/quote-001.png",
                42L,
                "image/png",
                "hash123",
                1024L,
                ProposalAssetDirectory.MediaType.IMAGE,
                "imagen-3.0-generate-002",
                "Chair visual",
                "AI-Generated",
                "AGENT",
                "vextis_crm_agent",
                "corr-001",
                NOW
        );

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(existing));

        Optional<ProposalAssetDirectory.ProposalAssetView> found = directory.findByIdempotencyKey("demo-tenant", "idemp-001");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(ASSET_ID);
    }

    @Test
    void registerAssetThrowsConflictOnSameKeyWithDifferentPayload() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);

        ProposalAssetDirectory.ProposalAssetView existing = new ProposalAssetDirectory.ProposalAssetView(
                ASSET_ID,
                "quote-001",
                "gs://bucket/proposals/x/quote-001.png",
                42L,
                "image/png",
                "hash123",
                1024L,
                ProposalAssetDirectory.MediaType.IMAGE,
                "imagen-3.0-generate-002",
                "Chair visual",
                "AI-Generated",
                "AGENT",
                "vextis_crm_agent",
                "corr-001",
                NOW
        );

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(existing));

        // Replaying with a different prompt summary must be rejected
        assertThatThrownBy(() -> directory.registerAsset(
                new ProposalAssetDirectory.RegisterProposalAssetCommand(
                        "demo-tenant",
                        "quote-001",
                        "gs://bucket/proposals/x/quote-001.png",
                        42L,
                        "image/png",
                        "hash123",
                        1024L,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Completely different desk visual",
                        "AI-Generated",
                        "AGENT",
                        "vextis_crm_agent",
                        "corr-001",
                        "idemp-001"
                )
        )).isInstanceOf(ProposalAssetConflictException.class)
                .hasMessageContaining("Idempotency-Key was already used to register a different proposal asset");
    }

    @Test
    void findByQuoteIdAppliesBoundedLimit() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        directory.findByQuoteId("demo-tenant", "quote-001", 100);

        // Max limit is clamped to 50
        verify(jdbc).query(anyString(), argThat((Map<String, Object> map) ->
                map.get("limit").equals(50) && map.get("quoteId").equals("quote-001")), any(RowMapper.class));
    }

    @Test
    void reserveInsertsPendingReservationWhenNoPriorReservation() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        String fingerprint = JdbcProposalAssetDirectory.computeFingerprint("quote-001", "Chair visual");
        ProposalAssetDirectory.ReservationResult result = directory.reserve(
                "demo-tenant", "quote-001", "idemp-res-1", fingerprint);

        assertThat(result.status()).isEqualTo(ProposalAssetDirectory.ReservationStatus.RESERVED);
        assertThat(result.isOwner()).isTrue();
        assertThat(result.reservationToken()).isNotBlank();
        assertThat(result.fingerprint()).isEqualTo(fingerprint);
    }

    @Test
    void reserveReturnsPendingForConcurrentCallerWhenLeaseActive() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);

        String fingerprint = JdbcProposalAssetDirectory.computeFingerprint("quote-001", "Chair visual");
        java.time.OffsetDateTime futureExpiry = java.time.OffsetDateTime.now().plusMinutes(5);
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("fingerprint", fingerprint, "status", "PENDING", "reservation_token", "token-orig", "expires_at", futureExpiry)
        ));

        ProposalAssetDirectory.ReservationResult result = directory.reserve(
                "demo-tenant", "quote-001", "idemp-res-1", fingerprint);

        assertThat(result.status()).isEqualTo(ProposalAssetDirectory.ReservationStatus.PENDING);
        assertThat(result.isOwner()).isFalse();
        assertThat(result.reservationToken()).isNull();
    }

    @Test
    void reserveAllowsTakeoverWhenPreviousOwnerLeaseExpired() {
        when(jdbc.update(anyString(), anyMap()))
                .thenReturn(0) // Initial insert fails (row exists)
                .thenReturn(1); // Takeover update succeeds

        String fingerprint = JdbcProposalAssetDirectory.computeFingerprint("quote-001", "Chair visual");
        java.time.OffsetDateTime pastExpiry = java.time.OffsetDateTime.now().minusMinutes(5);
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("fingerprint", fingerprint, "status", "PENDING", "reservation_token", "token-crashed-owner", "expires_at", pastExpiry)
        ));

        ProposalAssetDirectory.ReservationResult result = directory.reserve(
                "demo-tenant", "quote-001", "idemp-res-1", fingerprint);

        assertThat(result.status()).isEqualTo(ProposalAssetDirectory.ReservationStatus.RESERVED);
        assertThat(result.isOwner()).isTrue();
        assertThat(result.reservationToken()).isNotBlank();
        assertThat(result.reservationToken()).isNotEqualTo("token-crashed-owner");
    }

    @Test
    void registerAssetFailsWhenReservationTokenIsInvalid() {
        java.time.OffsetDateTime futureExpiry = java.time.OffsetDateTime.now().plusMinutes(5);
        String fingerprint = JdbcProposalAssetDirectory.computeFingerprint("quote-001", "Chair visual");
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("fingerprint", fingerprint, "status", "PENDING", "reservation_token", "correct-token-123", "expires_at", futureExpiry)
        ));

        assertThatThrownBy(() -> directory.registerAsset(
                new ProposalAssetDirectory.RegisterProposalAssetCommand(
                        "demo-tenant",
                        "quote-001",
                        "gs://bucket/proposals/x/quote-001.png",
                        42L,
                        "image/png",
                        "hash123",
                        1024L,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "Chair visual",
                        "AI-Generated",
                        "AGENT",
                        "vextis_crm_agent",
                        "corr-001",
                        "idemp-001",
                        "wrong-token-999"
                )
        )).isInstanceOf(ProposalAssetConflictException.class)
                .hasMessageContaining("Invalid or missing reservation token");
    }

    @Test
    void reserveThrowsConflictWhenFingerprintMismatches() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);

        String existingFingerprint = "fingerprint-original";
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("fingerprint", existingFingerprint, "status", "PENDING", "reservation_token", "token-orig", "expires_at", java.time.OffsetDateTime.now().plusMinutes(5))
        ));

        String newFingerprint = "fingerprint-different";
        assertThatThrownBy(() -> directory.reserve(
                "demo-tenant", "quote-001", "idemp-res-1", newFingerprint))
                .isInstanceOf(ProposalAssetConflictException.class)
                .hasMessageContaining("different payload fingerprint");
    }
}
