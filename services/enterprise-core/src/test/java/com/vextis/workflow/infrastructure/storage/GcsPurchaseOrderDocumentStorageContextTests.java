package com.vextis.workflow.infrastructure.storage;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GcsPurchaseOrderDocumentStorageContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "vextis.documents.bucket-name=vextis-assets",
                    "vextis.documents.signing-service-account=signer@example.iam.gserviceaccount.com")
            .withBean(Storage.class, () -> mock(Storage.class))
            .withBean(GcsPurchaseOrderDocumentStorage.class);

    @Test
    void startsWithTheProductionConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GcsPurchaseOrderDocumentStorage.class);
        });
    }
}
