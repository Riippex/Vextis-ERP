package com.vextis.crm;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GcsProposalAssetStorageContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("vextis.crm.proposal-assets.bucket-name=vextis-assets")
            .withBean(Storage.class, () -> mock(Storage.class))
            .withBean(GcsProposalAssetStorage.class);

    @Test
    void startsWithTheProductionConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GcsProposalAssetStorage.class);
        });
    }
}
