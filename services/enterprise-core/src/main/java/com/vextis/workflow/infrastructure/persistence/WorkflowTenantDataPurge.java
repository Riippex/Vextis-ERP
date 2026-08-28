package com.vextis.workflow.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes workflow executions and the purchase orders that sourced them.
 * Plans, steps, order lines, readiness, approvals and timeline entries cascade
 * from the execution. Outbox events and idempotency records go too: a
 * surviving idempotency record would make the next run replay a stored
 * response instead of executing, which is exactly the non-determinism a reset
 * has to clear.
 */
@Component
class WorkflowTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    WorkflowTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String area() {
        return "workflows";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM workflow_executions WHERE tenant_id = :tenantId
                """, params);
        removed += jdbc.update("""
                DELETE FROM workflow_purchase_orders WHERE tenant_id = :tenantId
                """, params);
        removed += jdbc.update("""
                DELETE FROM outbox_events WHERE tenant_id = :tenantId
                """, params);
        removed += jdbc.update("""
                DELETE FROM idempotency_records WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
