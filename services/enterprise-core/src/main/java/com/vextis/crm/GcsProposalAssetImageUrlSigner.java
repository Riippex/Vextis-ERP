package com.vextis.crm;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Produces a short-lived HTTPS URL a browser can load directly, since a
 * {@code gs://} URI cannot be rendered in an {@code <img>} tag and the
 * proposal assets bucket is not public.
 */
@Component
public class GcsProposalAssetImageUrlSigner {

    private static final Logger log = LoggerFactory.getLogger(GcsProposalAssetImageUrlSigner.class);
    private static final Duration URL_TTL = Duration.ofMinutes(15);
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final Storage storage;
    private final String bucketName;
    private final Supplier<ServiceAccountSigner> signerProvider;

    @Autowired
    public GcsProposalAssetImageUrlSigner(
            Storage storage,
            @Value("${vextis.crm.proposal-assets.bucket-name:}") String bucketName,
            @Value("${vextis.documents.signing-service-account:}") String signingServiceAccount
    ) {
        this(storage, bucketName, memoizedSigner(signingServiceAccount));
    }

    GcsProposalAssetImageUrlSigner(
            Storage storage, String bucketName, Supplier<ServiceAccountSigner> signerProvider
    ) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.signerProvider = signerProvider;
    }

    /**
     * Returns a signed HTTPS URL for the given {@code gs://} URI, or empty if
     * this deployment cannot sign URLs (not configured) or the URI is not
     * one of this bucket's own objects. Never throws: a signing hiccup
     * degrades to no image, not a broken query.
     */
    public Optional<String> signedImageUrl(String storageUri) {
        return signedImageUrl(storageUri, null);
    }

    /**
     * Returns a signed HTTPS URL pinned to the exact registered generation for the given
     * {@code gs://} URI, guaranteeing that overwritten objects cannot alter what
     * was signed.
     */
    public Optional<String> signedImageUrl(String storageUri, Long generation) {
        if (bucketName == null || bucketName.isBlank()) {
            return Optional.empty();
        }
        String prefix = "gs://" + bucketName + "/";
        if (storageUri == null || !storageUri.startsWith(prefix)) {
            return Optional.empty();
        }
        String objectName = storageUri.substring(prefix.length());
        try {
            BlobInfo blob = generation != null
                    ? BlobInfo.newBuilder(com.google.cloud.storage.BlobId.of(bucketName, objectName, generation)).build()
                    : BlobInfo.newBuilder(bucketName, objectName).build();
            URL signedUrl = storage.signUrl(
                    blob,
                    URL_TTL.toMinutes(),
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.signWith(signerProvider.get()),
                    Storage.SignUrlOption.withV4Signature()
            );
            return Optional.of(signedUrl.toString());
        } catch (RuntimeException exception) {
            log.warn("Could not sign proposal asset image URL for {}: {}", storageUri, exception.getMessage());
            return Optional.empty();
        }
    }

    private static Supplier<ServiceAccountSigner> memoizedSigner(String signingServiceAccount) {
        return new Supplier<>() {
            private volatile ServiceAccountSigner signer;

            @Override
            public ServiceAccountSigner get() {
                ServiceAccountSigner current = signer;
                if (current == null) {
                    synchronized (this) {
                        current = signer;
                        if (current == null) {
                            current = createSigner(signingServiceAccount);
                            signer = current;
                        }
                    }
                }
                return current;
            }
        };
    }

    private static ServiceAccountSigner createSigner(String signingServiceAccount) {
        if (signingServiceAccount == null || signingServiceAccount.isBlank()) {
            throw new IllegalStateException("Proposal asset image signing identity is not configured");
        }
        try {
            GoogleCredentials source = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            return ImpersonatedCredentials.create(
                    source,
                    signingServiceAccount,
                    null,
                    List.of(CLOUD_PLATFORM_SCOPE),
                    Math.toIntExact(URL_TTL.toSeconds())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Google application credentials are unavailable", exception);
        }
    }
}
