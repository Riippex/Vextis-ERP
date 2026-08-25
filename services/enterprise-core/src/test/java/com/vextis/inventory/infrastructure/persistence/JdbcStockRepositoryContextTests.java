package com.vextis.inventory.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JdbcStockRepositoryContextTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
            .withBean(JdbcStockRepository.class);

    @Test
    void startsWithSpringBootManagedJacksonMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(JdbcStockRepository.class);
        });
    }
}
