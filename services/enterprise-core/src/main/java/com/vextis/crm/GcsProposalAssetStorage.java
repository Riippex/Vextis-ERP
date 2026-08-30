package com.vextis.crm;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Confirms a registered proposal asset actually exists where a tenant is
 * authorized to write, before Enterprise Core records it as real. A
 * quote/proposal asset is only ever a {@code gs://} object inside the
 * configured bucket, under this tenant's own SHA-256-derived prefix — never
 * an arbitrary {@code gs://}, {@code https://}, or {@code urn:} value.
 */
@Component
public class GcsProposalAssetStorage {

    private final Storage storage;
    private final String bucketName;

    @Autowired
    public GcsProposalAssetStorage(
            Storage storage,
            @Value("${vextis.crm.proposal-assets.bucket-name:}") String bucketName
    ) {
        this.storage = storage;
        this.bucketName = bucketName;
    }

    /**
     * Throws {@link IllegalStateException} if this deployment has no
     * proposal assets bucket configured, or {@link IllegalArgumentException}
     * if the URI does not belong to this tenant's authorized bucket/prefix or
     * the object was not actually written.
     */
    public void assertUploaded(String tenantId, String storageUri) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("Vextis proposal asset storage is not configured");
        }
        String expectedPrefix = "gs://" + bucketName + "/" + objectPrefix(tenantId);
        if (!storageUri.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "Proposal asset URI does not belong to the authorized tenant bucket or prefix");
        }
        String objectName = storageUri.substring(("gs://" + bucketName + "/").length());
        if (objectName.isBlank() || objectName.contains("?") || objectName.contains("#")
                || objectName.contains("..")) {
            throw new IllegalArgumentException("Proposal asset URI is invalid");
        }

        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null) {
            throw new IllegalArgumentException("Proposal asset was not found in Cloud Storage");
        }
        Long size = blob.getSize();
        if (size == null || size < 1) {
            throw new IllegalArgumentException("Proposal asset upload is empty or incomplete");
        }
    }

    static String objectPrefix(String tenantId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tenantId.getBytes(StandardCharsets.UTF_8));
            return "proposals/" + HexFormat.of().formatHex(digest, 0, 12) + "/";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
