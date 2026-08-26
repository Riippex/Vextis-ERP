package com.vextis.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcAuditTrailTests {

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
        assertThat(parameters.getValue().getValue("occurredAt")).isEqualTo(occurredAt);
    }
}
