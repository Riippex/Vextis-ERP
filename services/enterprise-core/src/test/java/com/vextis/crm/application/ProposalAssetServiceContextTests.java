package com.vextis.crm.application;

import com.vextis.audit.AuditTrail;
import com.vextis.crm.GcsProposalAssetStorage;
import com.vextis.crm.ProposalAssetDirectory;
import com.vextis.crm.QuoteExecutionLookup;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProposalAssetServiceContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(ProposalAssetDirectory.class, () -> mock(ProposalAssetDirectory.class))
            .withBean(QuoteExecutionLookup.class, () -> mock(QuoteExecutionLookup.class))
            .withBean(GcsProposalAssetStorage.class, () -> mock(GcsProposalAssetStorage.class))
            .withBean(AuditTrail.class, () -> mock(AuditTrail.class))
            .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ProposalAssetService.class);

    @Test
    void startsWithSpringBootManagedJacksonMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(ProposalAssetService.class);
        });
    }
}
