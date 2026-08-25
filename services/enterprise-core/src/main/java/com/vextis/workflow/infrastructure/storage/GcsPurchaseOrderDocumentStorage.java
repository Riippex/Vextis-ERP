package com.vextis.workflow.infrastructure.storage;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.PostPolicyV4;
import com.google.cloud.storage.Storage;
import com.vextis.workflow.application.port.PurchaseOrderDocumentStorage;
import com.vextis.workflow.domain.PurchaseOrderUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
class GcsPurchaseOrderDocumentStorage implements PurchaseOrderDocumentStorage {
    static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");

    private final Storage storage;
    private final Clock clock;
    private final String bucketName;
    private final Supplier<ServiceAccountSigner> signerProvider;

    GcsPurchaseOrderDocumentStorage(
            Storage storage,
            @Value("${vextis.documents.bucket-name:}") String bucketName,
            @Value("${vextis.documents.signing-service-account:}") String signingServiceAccount
    ) {
        this(storage, Clock.systemUTC(), bucketName, memoizedSigner(signingServiceAccount));
    }

    GcsPurchaseOrderDocumentStorage(
            Storage storage,
            Clock clock,
            String bucketName,
            Supplier<ServiceAccountSigner> signerProvider
    ) {
        this.storage = storage;
        this.clock = clock;
        this.bucketName = bucketName;
        this.signerProvider = signerProvider;
    }

    @Override
    public PurchaseOrderUpload prepareUpload(
            String tenantId, String fileName, String contentType, int sizeBytes) {
        assertConfigured();
        String normalizedContentType = normalizeContentType(contentType);
        if (sizeBytes < 1 || sizeBytes > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Purchase order document must be between 1 byte and 10 MiB");
        }
        if (fileName.length() > 255) {
            throw new IllegalArgumentException("File name must not exceed 255 characters");
        }

        String objectName = objectPrefix(tenantId) + UUID.randomUUID() + extensionFor(normalizedContentType);
        BlobInfo blob = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(normalizedContentType)
                .build();
        PostPolicyV4.PostFieldsV4 fields = PostPolicyV4.PostFieldsV4.newBuilder()
                .setContentType(normalizedContentType)
                .setSuccessActionStatus(204)
                .build();
        PostPolicyV4.PostConditionsV4 conditions = PostPolicyV4.PostConditionsV4.newBuilder()
                .addContentTypeCondition(PostPolicyV4.ConditionV4Type.MATCHES, normalizedContentType)
                .addContentLengthRangeCondition(sizeBytes, sizeBytes)
                .addSuccessActionStatusCondition(204)
                .build();
        PostPolicyV4 policy = storage.generateSignedPostPolicyV4(
                blob,
                UPLOAD_TTL.toSeconds(),
                TimeUnit.SECONDS,
                fields,
                conditions,
                Storage.PostPolicyV4Option.signWith(signerProvider.get())
        );
        Instant expiresAt = clock.instant().plus(UPLOAD_TTL);
        return new PurchaseOrderUpload(
                policy.getUrl(),
                "gs://" + bucketName + "/" + objectName,
                expiresAt,
                policy.getFields().entrySet().stream()
                        .map(entry -> new PurchaseOrderUpload.FormField(entry.getKey(), entry.getValue()))
                        .sorted(java.util.Comparator.comparing(PurchaseOrderUpload.FormField::name))
                        .toList()
        );
    }

    @Override
    public void assertReady(String tenantId, String documentUri) {
        assertConfigured();
        String expectedPrefix = "gs://" + bucketName + "/" + objectPrefix(tenantId);
        if (!documentUri.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Document does not belong to this tenant upload area");
        }
        String objectName = documentUri.substring(("gs://" + bucketName + "/").length());
        if (objectName.isBlank() || objectName.contains("?") || objectName.contains("#")) {
            throw new IllegalArgumentException("Document URI is invalid");
        }
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null) {
            throw new IllegalArgumentException("Uploaded purchase order document was not found");
        }
        Long size = blob.getSize();
        if (size == null || size < 1 || size > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Uploaded purchase order document has an invalid size");
        }
        normalizeContentType(blob.getContentType());
    }

    private void assertConfigured() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("Vextis purchase order document storage is not configured");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("Purchase order content type is required");
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Only PDF, JPEG, and PNG purchase orders are supported");
        }
        return normalized;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalArgumentException("Unsupported purchase order content type");
        };
    }

    private static String objectPrefix(String tenantId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tenantId.getBytes(StandardCharsets.UTF_8));
            return "purchase-orders/" + HexFormat.of().formatHex(digest, 0, 12) + "/";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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
            throw new IllegalStateException("Purchase order upload signing identity is not configured");
        }
        try {
            GoogleCredentials source = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            return ImpersonatedCredentials.create(
                    source,
                    signingServiceAccount,
                    null,
                    List.of(CLOUD_PLATFORM_SCOPE),
                    Math.toIntExact(UPLOAD_TTL.toSeconds())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Google application credentials are unavailable", exception);
        }
    }
}
