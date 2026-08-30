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

        assets.assertUploaded("demo-tenant", storageUri);
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
