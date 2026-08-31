package com.vextis.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcAuditTrail implements AuditTrail {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAuditTrail(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordUserAction(UserAction action) {
        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action,
                     resource_type, resource_id, result, occurred_at)
                VALUES
                    (:id, :tenantId, :correlationId, 'USER', :actorId, :action,
                     :resourceType, :resourceId, 'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", action.tenantId())
                        .addValue("correlationId", action.correlationId())
                        .addValue("actorId", action.actorId())
                        .addValue("action", action.action())
                        .addValue("resourceType", action.resourceType())
                        .addValue("resourceId", action.resourceId())
                        .addValue(
                                "occurredAt",
                                Timestamp.from(action.occurredAt()),
                                Types.TIMESTAMP_WITH_TIMEZONE)
        );
    }

    @Override
    public void recordAgentDecision(AgentDecision decision) {
        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action,
                     resource_type, resource_id, result, occurred_at)
                VALUES
                    (:id, :tenantId, :correlationId, 'AGENT', :actorId, :action,
                     :resourceType, :resourceId, :result, :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", decision.tenantId())
                        .addValue("correlationId", decision.correlationId())
                        .addValue("actorId", decision.agentId())
                        .addValue("action", decision.action())
                        .addValue("resourceType", decision.resourceType())
                        .addValue("resourceId", decision.resourceId())
                        .addValue("result", decision.result().name())
                        .addValue(
                                "occurredAt",
                                Timestamp.from(decision.occurredAt()),
                                Types.TIMESTAMP_WITH_TIMEZONE)
        );
    }

    @Override
    public List<AuditRecord> findByCorrelation(String tenantId, String correlationId) {
        return jdbc.query(
                """
                SELECT id, correlation_id, actor_type, actor_id, action,
                       resource_type, resource_id, result, occurred_at
                FROM audit_records
                WHERE tenant_id = :tenantId AND correlation_id = :correlationId
                ORDER BY occurred_at DESC, id DESC
                LIMIT 100
                """,
                Map.of("tenantId", tenantId, "correlationId", correlationId),
                (rs, row) -> new AuditRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("correlation_id"),
                        rs.getString("actor_type"),
                        rs.getString("actor_id"),
                        rs.getString("action"),
                        rs.getString("resource_type"),
                        rs.getObject("resource_id", UUID.class),
                        rs.getString("result"),
                        rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant()
                )
        );
    }
}
