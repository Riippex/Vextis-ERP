package com.vextis.workflow.domainrules;

import com.vextis.workflow.domain.PurchaseOrderSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderSourceTests {

    @Test
    void rejectsDocumentsOutsideGoogleCloudStorage() {
        assertThatThrownBy(() -> new PurchaseOrderSource(
                UUID.randomUUID(),
                "demo-tenant",
                "PO-001",
                "Acme",
                "https://example.com/order.pdf",
                Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Document URI must point to Google Cloud Storage");
    }
}
