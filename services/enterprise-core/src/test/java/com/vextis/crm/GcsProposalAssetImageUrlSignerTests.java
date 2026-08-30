package com.vextis.crm;

import com.google.auth.ServiceAccountSigner;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GcsProposalAssetImageUrlSignerTests {

    @Test
    void signsAnHttpsUrlForAnObjectInTheConfiguredBucket() throws Exception {
        Storage storage = mock(Storage.class);
        URL signed = URI.create(
                "https://storage.googleapis.com/vextis-assets/proposals/x/quote-001.png?sig=abc").toURL();
        when(storage.signUrl(
                any(BlobInfo.class),
                eq(15L),
                eq(TimeUnit.MINUTES),
                any(Storage.SignUrlOption.class),
                any(Storage.SignUrlOption.class)))
                .thenReturn(signed);
        GcsProposalAssetImageUrlSigner signer = new GcsProposalAssetImageUrlSigner(
                storage, "vextis-assets", FakeSigner::new);

        var result = signer.signedImageUrl("gs://vextis-assets/proposals/x/quote-001.png");

        assertThat(result).contains(signed.toString());
        verify(storage).signUrl(
                eq(BlobInfo.newBuilder("vextis-assets", "proposals/x/quote-001.png").build()),
                eq(15L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class));
    }

    @Test
    void returnsEmptyWhenNoBucketIsConfigured() {
        GcsProposalAssetImageUrlSigner signer = new GcsProposalAssetImageUrlSigner(
                mock(Storage.class), "", FakeSigner::new);

        assertThat(signer.signedImageUrl("gs://vextis-assets/proposals/x/quote-001.png")).isEmpty();
    }

    @Test
    void returnsEmptyForAUriOutsideTheConfiguredBucket() {
        GcsProposalAssetImageUrlSigner signer = new GcsProposalAssetImageUrlSigner(
                mock(Storage.class), "vextis-assets", FakeSigner::new);

        assertThat(signer.signedImageUrl("gs://some-other-bucket/quote-001.png")).isEmpty();
    }

    @Test
    void returnsEmptyRatherThanThrowingWhenSigningFails() {
        Storage storage = mock(Storage.class);
        when(storage.signUrl(any(BlobInfo.class), eq(15L), eq(TimeUnit.MINUTES),
                any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                .thenThrow(new RuntimeException("impersonation unavailable"));
        GcsProposalAssetImageUrlSigner signer = new GcsProposalAssetImageUrlSigner(
                storage, "vextis-assets", FakeSigner::new);

        assertThat(signer.signedImageUrl("gs://vextis-assets/proposals/x/quote-001.png")).isEmpty();
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
