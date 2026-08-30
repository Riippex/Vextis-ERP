package com.vextis.crm;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GcsProposalAssetStorageTests {

    @Test
    void confirmsAnExistingObjectUnderTheTenantsOwnPrefix() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");
        String prefix = GcsProposalAssetStorage.objectPrefix("demo-tenant");
        String storageUri = "gs://vextis-assets/" + prefix + "quote-001_abc123.png";
        Blob blob = mock(Blob.class);
        when(blob.getSize()).thenReturn(2048L);
        when(storage.get(BlobId.of("vextis-assets", prefix + "quote-001_abc123.png"))).thenReturn(blob);

        when(blob.getGeneration()).thenReturn(42L);
        when(blob.getContentType()).thenReturn("image/png");
        when(blob.getMd5ToHexString()).thenReturn("d41d8cd98f00b204e9800998ecf8427e");

        GcsProposalAssetStorage.AssetObjectMetadata meta = assets.assertUploaded("demo-tenant", storageUri);
        org.assertj.core.api.Assertions.assertThat(meta.generation()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(meta.contentType()).isEqualTo("image/png");
        org.assertj.core.api.Assertions.assertThat(meta.contentHash()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        org.assertj.core.api.Assertions.assertThat(meta.sizeBytes()).isEqualTo(2048L);
    }

    @Test
    void rejectsAnObjectExceedingMaxSize() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");
        String prefix = GcsProposalAssetStorage.objectPrefix("demo-tenant");
        String storageUri = "gs://vextis-assets/" + prefix + "huge.png";
        Blob blob = mock(Blob.class);
        when(blob.getSize()).thenReturn(30 * 1024 * 1024L); // 30 MB > 25 MB limit
        when(storage.get(BlobId.of("vextis-assets", prefix + "huge.png"))).thenReturn(blob);

        assertThatThrownBy(() -> assets.assertUploaded("demo-tenant", storageUri, ProposalAssetDirectory.MediaType.IMAGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum permitted");
    }

    @Test
    void rejectsContentTypeMismatchForImage() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");
        String prefix = GcsProposalAssetStorage.objectPrefix("demo-tenant");
        String storageUri = "gs://vextis-assets/" + prefix + "not-an-image.png";
        Blob blob = mock(Blob.class);
        when(blob.getSize()).thenReturn(2048L);
        when(blob.getContentType()).thenReturn("video/mp4");
        when(storage.get(BlobId.of("vextis-assets", prefix + "not-an-image.png"))).thenReturn(blob);

        assertThatThrownBy(() -> assets.assertUploaded("demo-tenant", storageUri, ProposalAssetDirectory.MediaType.IMAGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared as IMAGE but Cloud Storage content type is 'video/mp4'");
    }

    @Test
    void rejectsAnObjectOutsideTheTenantsOwnPrefix() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");
        String otherTenantPrefix = GcsProposalAssetStorage.objectPrefix("another-tenant");

        assertThatThrownBy(() -> assets.assertUploaded(
                "demo-tenant", "gs://vextis-assets/" + otherTenantPrefix + "quote-001.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal asset URI does not belong to the authorized tenant bucket or prefix");
    }

    @Test
    void rejectsAnObjectInAnUnauthorizedBucket() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");

        assertThatThrownBy(() -> assets.assertUploaded(
                "demo-tenant", "gs://some-other-bucket/whatever.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal asset URI does not belong to the authorized tenant bucket or prefix");
    }

    @Test
    void rejectsAnHttpsOrUrnUri() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");

        assertThatThrownBy(() -> assets.assertUploaded(
                "demo-tenant", "https://storage.googleapis.com/vextis-assets/whatever.png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assets.assertUploaded("demo-tenant", "urn:vextis:proposal-asset:1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnObjectThatWasNeverActuallyWritten() {
        Storage storage = mock(Storage.class);
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(storage, "vextis-assets");
        String prefix = GcsProposalAssetStorage.objectPrefix("demo-tenant");
        String storageUri = "gs://vextis-assets/" + prefix + "quote-001_abc123.png";
        when(storage.get(BlobId.of("vextis-assets", prefix + "quote-001_abc123.png"))).thenReturn(null);

        assertThatThrownBy(() -> assets.assertUploaded("demo-tenant", storageUri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal asset was not found in Cloud Storage");
    }

    @Test
    void rejectsWhenNoBucketIsConfigured() {
        GcsProposalAssetStorage assets = new GcsProposalAssetStorage(mock(Storage.class), "");

        assertThatThrownBy(() -> assets.assertUploaded("demo-tenant", "gs://vextis-assets/whatever.png"))
                .isInstanceOf(IllegalStateException.class);
    }
}
