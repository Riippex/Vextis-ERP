package com.vextis.workflow.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPurchaseOrderWorkflowRepositoryTests {

    @Test
    void convertsInstantsToUtcOffsetDateTimesForPostgres() {
        Instant instant = Instant.parse("2026-08-24T17:00:00.123Z");

        var timestamp = JdbcPurchaseOrderWorkflowRepository.sqlTimestamp(instant);

        assertThat(timestamp.toInstant()).isEqualTo(instant);
        assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void convertsPostgresOffsetDateTimesBackToInstants() throws Exception {
        var resultSet = mock(java.sql.ResultSet.class);
        var stored = OffsetDateTime.parse("2026-08-24T12:00:00.123-05:00");
        when(resultSet.getObject("occurred_at", OffsetDateTime.class)).thenReturn(stored);

        var instant = JdbcPurchaseOrderWorkflowRepository.readInstant(resultSet, "occurred_at");

        assertThat(instant).isEqualTo(Instant.parse("2026-08-24T17:00:00.123Z"));
    }
}
