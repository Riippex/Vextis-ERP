package com.vextis.crm.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes tenant customers. Runs last because workflow, billing and inventory
 * rows reference customers by value and are already gone by this point.
 */
@Component
class CrmTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    CrmTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public String area() {
        return "customers";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM crm_customers WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
