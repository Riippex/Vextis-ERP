package com.vextis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationConfigurationTests {

    @Test
    void packagesSpringBootFlywayAutoConfiguration() {
        assertThat(FlywayAutoConfiguration.class).isNotNull();
    }
}
