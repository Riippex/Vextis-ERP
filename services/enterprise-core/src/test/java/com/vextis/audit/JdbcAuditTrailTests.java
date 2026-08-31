package com.vextis.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcAuditTrailTests {

    @Test
    void persistsUserActionWithAnExplicitPostgresTimestampType() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcAuditTrail auditTrail = new JdbcAuditTrail(jdbc);
        UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant occurredAt = Instant.parse("2026-08-31T01:00:49Z");

        auditTrail.recordUserAction(new AuditTrail.UserAction(
                "demo-tenant",
                "corr-customer-001",
                "firebase-user-123",
                "crm.customer.saved",
                "Customer",
                customerId,
                occurredAt
        ));

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO audit_records"), parameters.capture());
        assertThat(parameters.getValue().getValue("actorId")).isEqualTo("firebase-user-123");
        assertThat(parameters.getValue().getValue("resourceId")).isEqualTo(customerId);
        assertThat(parameters.getValue().getValue("occurredAt"))
                .isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
        assertThat(parameters.getValue().getSqlType("occurredAt")).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    void persistsDeniedAgentDecisionWithoutCredentialsOrPromptContent() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcAuditTrail auditTrail = new JdbcAuditTrail(jdbc);
        UUID executionId = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");
        Instant occurredAt = Instant.parse("2026-08-26T18:00:00Z");

        auditTrail.recordAgentDecision(new AuditTrail.AgentDecision(
                "demo-tenant",
                "corr-001",
                "rogue-agent",
                "START_EXECUTION_PLANNING",
                "WORKFLOW_EXECUTION",
                executionId,
                AuditTrail.AgentDecisionResult.DENIED,
                occurredAt
        ));

        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO audit_records"), parameters.capture());
        assertThat(parameters.getValue().getValue("tenantId")).isEqualTo("demo-tenant");
        assertThat(parameters.getValue().getValue("actorId")).isEqualTo("rogue-agent");
        assertThat(parameters.getValue().getValue("resourceId")).isEqualTo(executionId);
        assertThat(parameters.getValue().getValue("result")).isEqualTo("DENIED");
        assertThat(parameters.getValue().getValue("occurredAt"))
                .isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
        assertThat(parameters.getValue().getSqlType("occurredAt")).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
