package com.vextis.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.ServiceAccountSigner;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.vextis.audit.AuditTrail;
import com.vextis.crm.application.ProposalAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end verification of the full lifecycle:
 * Preflight -> Storage Upload -> Core Registration -> Pinned URL Signing.
 */
class ProposalAssetFlowIntegrationTests {

    private static final UUID QUOTE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = "demo-tenant";
    private static final String CORRELATION_ID = "corr-flow-123";
    private static final Instant NOW = Instant.parse("2026-08-28T16:00:00Z");

    @Test
    void completeProposalAssetFlowFromUploadToSignedUrl() throws Exception {
        // 1. Setup mock Cloud Storage
        Storage storage = mock(Storage.class);
        String bucketName = "vextis-assets";
        String tenantPrefix = GcsProposalAssetStorage.objectPrefix(TENANT_ID);
        String objectName = tenantPrefix + QUOTE_ID + "_concept.png";
        String storageUri = "gs://" + bucketName + "/" + objectName;

        Blob blob = mock(Blob.class);
        when(blob.getSize()).thenReturn(8192L);
        when(blob.getContentType()).thenReturn("image/png");
        when(blob.getGeneration()).thenReturn(999888L);
        when(blob.getMd5ToHexString()).thenReturn("a1b2c3d4e5f6");
        when(storage.get(BlobId.of(bucketName, objectName))).thenReturn(blob);

        URL signedUrl = URI.create("https://storage.googleapis.com/vextis-assets/" + objectName + "?generation=999888&sig=xyz").toURL();
        when(storage.signUrl(any(BlobInfo.class), eq(15L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                .thenReturn(signedUrl);

        // 2. Setup Core Domain & Storage services
        GcsProposalAssetStorage assetStorage = new GcsProposalAssetStorage(storage, bucketName);
        GcsProposalAssetImageUrlSigner urlSigner = new GcsProposalAssetImageUrlSigner(
                storage, bucketName, () -> new ServiceAccountSigner() {
            @Override
            public String getAccount() {
                return "signer@vextis.iam.gserviceaccount.com";
            }

            @Override
            public byte[] sign(byte[] toSign) {
                return new byte[]{1, 2, 3};
            }
        });

        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        ProposalAssetDirectory directory = new JdbcProposalAssetDirectory(jdbc);
        QuoteExecutionLookup quoteLookup = mock(QuoteExecutionLookup.class);
        when(quoteLookup.findQuote(TENANT_ID, QUOTE_ID)).thenReturn(Optional.of(
                new QuoteExecutionLookup.QuoteExecution(QUOTE_ID, TENANT_ID, CORRELATION_ID)));

        AuditTrail audit = mock(AuditTrail.class);
        ProposalAssetService service = new ProposalAssetService(
                directory, quoteLookup, assetStorage, audit, jdbc, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        // Step A: Preflight check before spending generation resources
        RegisterProposalAssetUseCase.PreflightResult preflight = service.preflight(
                new RegisterProposalAssetUseCase.PreflightCommand(
                        TENANT_ID, "vextis_crm_agent", QUOTE_ID, CORRELATION_ID, "idemp-flow-001", "3D render of ergonomic chair"));
        assertThat(preflight.authorized()).isTrue();
        assertThat(preflight.tenantPrefix()).isEqualTo(tenantPrefix);
        assertThat(preflight.reservationToken()).isNotNull();

        // Step B: Core registers the confirmed upload
        ProposalAssetDirectory.ProposalAssetView asset = service.registerAsset(
                new RegisterProposalAssetUseCase.RegisterCommand(
                        TENANT_ID,
                        "vextis_crm_agent",
                        QUOTE_ID,
                        CORRELATION_ID,
                        "idemp-flow-001",
                        storageUri,
                        ProposalAssetDirectory.MediaType.IMAGE,
                        "imagen-3.0-generate-002",
                        "3D render of ergonomic chair",
                        "AI-Generated Proposal Concept",
                        preflight.reservationToken()
                )
        );

        assertThat(asset.quoteId()).isEqualTo(QUOTE_ID.toString());
        assertThat(asset.storageGeneration()).isEqualTo(999888L);
        assertThat(asset.contentType()).isEqualTo("image/png");

        // Step C: Generate generation-pinned signed URL for web browser
        Optional<String> browserUrl = urlSigner.signedImageUrl(asset.storageUri(), asset.storageGeneration());
        assertThat(browserUrl).isPresent();
        assertThat(browserUrl.get()).contains("generation=999888");
        assertThat(browserUrl.get()).startsWith("https://storage.googleapis.com/");

        // Step D: Verify audit and outbox were triggered
        verify(audit).recordAgentDecision(any());
        verify(jdbc).update(eq("""
                INSERT INTO outbox_events
                    (event_id, event_type, event_version, aggregate_type, aggregate_id, tenant_id,
                     correlation_id, causation_id, payload, occurred_at)
                VALUES (:eventId, 'quote.visual.generated', 1, 'PROPOSAL_ASSET',
                        :aggregateId, :tenantId, :correlationId, :causationId, CAST(:payload AS JSONB), :occurredAt)
                ON CONFLICT (event_id) DO NOTHING
                """), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
    }
}
