package com.vextis.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JdbcProposalAssetDirectoryContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
            .withUserConfiguration(ProposalAssetDirectoryConfiguration.class);

    @Test
    void selectsTheProductionConstructorWhenSpringCreatesTheRepository() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JdbcProposalAssetDirectory.class);
        });
    }

    @Import(JdbcProposalAssetDirectory.class)
    static class ProposalAssetDirectoryConfiguration {
    }
}
