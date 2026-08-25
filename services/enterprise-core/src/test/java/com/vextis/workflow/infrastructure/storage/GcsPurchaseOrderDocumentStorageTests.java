package com.vextis.workflow.infrastructure.storage;

import com.google.auth.ServiceAccountSigner;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.PostPolicyV4;
import com.google.cloud.storage.Storage;
import com.vextis.workflow.domain.PurchaseOrderUpload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GcsPurchaseOrderDocumentStorageTests {
    private static final Instant NOW = Instant.parse("2026-08-24T22:30:00Z");

    @Test
    void preparesShortLivedTypedUploadAndVerifiesStoredObject() throws Exception {
        Storage storage = mock(Storage.class);
        ServiceAccountSigner signer = new FakeSigner();
        when(storage.generateSignedPostPolicyV4(
                any(BlobInfo.class),
                eq(600L),
                eq(TimeUnit.SECONDS),
                any(PostPolicyV4.PostFieldsV4.class),
                any(PostPolicyV4.PostConditionsV4.class),
                any(Storage.PostPolicyV4Option[].class)))
                .thenReturn(PostPolicyV4.of(
                        "https://storage.googleapis.com/signed-upload",
                        Map.of("Content-Type", "application/pdf")));
        GcsPurchaseOrderDocumentStorage documents = new GcsPurchaseOrderDocumentStorage(
                storage, Clock.fixed(NOW, ZoneOffset.UTC), "vextis-assets", () -> signer);

        PurchaseOrderUpload upload = documents.prepareUpload(
                "demo-tenant", "customer-order.pdf", "application/pdf", 2048);

        assertThat(upload.uploadUrl()).isEqualTo("https://storage.googleapis.com/signed-upload");
        assertThat(upload.documentUri()).startsWith("gs://vextis-assets/purchase-orders/").endsWith(".pdf");
        assertThat(upload.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(upload.formFields()).containsExactly(
                new PurchaseOrderUpload.FormField("Content-Type", "application/pdf"));

        String objectName = upload.documentUri().substring("gs://vextis-assets/".length());
        Blob blob = mock(Blob.class);
        when(blob.getSize()).thenReturn(2048L);
        when(blob.getContentType()).thenReturn("application/pdf");
        when(storage.get(BlobId.of("vextis-assets", objectName))).thenReturn(blob);

        documents.assertReady("demo-tenant", upload.documentUri());

        verify(storage).get(BlobId.of("vextis-assets", objectName));
    }

    @Test
    void rejectsAnotherTenantsObjectBeforeReadingStorage() {
        GcsPurchaseOrderDocumentStorage documents = new GcsPurchaseOrderDocumentStorage(
                mock(Storage.class), Clock.fixed(NOW, ZoneOffset.UTC), "vextis-assets", FakeSigner::new);

        assertThatThrownBy(() -> documents.assertReady(
                "another-tenant", "gs://vextis-assets/purchase-orders/not-that-tenant/document.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Document does not belong to this tenant upload area");
    }

    @Test
    void rejectsUnsupportedOrOversizedDocuments() {
        GcsPurchaseOrderDocumentStorage documents = new GcsPurchaseOrderDocumentStorage(
                mock(Storage.class), Clock.fixed(NOW, ZoneOffset.UTC), "vextis-assets", FakeSigner::new);

        assertThatThrownBy(() -> documents.prepareUpload(
                "demo-tenant", "order.svg", "image/svg+xml", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only PDF, JPEG, and PNG purchase orders are supported");
        assertThatThrownBy(() -> documents.prepareUpload(
                "demo-tenant", "order.pdf", "application/pdf", 10 * 1024 * 1024 + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Purchase order document must be between 1 byte and 10 MiB");
    }

    private static final class FakeSigner implements ServiceAccountSigner {
        @Override
        public String getAccount() {
            return "signer@example.iam.gserviceaccount.com";
        }

        @Override
        public byte[] sign(byte[] toSign) {
            return new byte[]{1, 2, 3};
        }
    }
}
