package com.vextis.billing.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JdbcInvoiceRepositoryTests {

    private static final String ADVISORY_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))";

    @Test
    void executesAdvisoryLocksWithoutReadingPostgresVoidResults() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcInvoiceRepository repository = new JdbcInvoiceRepository(jdbc, mock(ObjectMapper.class));
        UUID orderId = UUID.fromString("c127f8ac-6eec-4387-a552-1a41213f5ea8");

        repository.acquireLocks("demo-tenant", orderId, "event-001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(2)).execute(
                eq(ADVISORY_LOCK_SQL), parameters.capture(), any(PreparedStatementCallback.class));
        assertThat(parameters.getAllValues())
                .extracting(source -> source.getValue("lockKey"))
                .containsExactly(
                        "demo-tenant:invoice:idempotency:event-001",
                        "demo-tenant:invoice:order:" + orderId);
    }
}
