package com.vextis.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
                        .addValue("occurredAt", action.occurredAt())
        );
    }
}
