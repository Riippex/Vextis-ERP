package com.vextis.inventory.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes stock reservations and the stock rows they draw down, so
 * availability returns to whatever the next seed writes rather than the
 * previous run leftovers.
 */
@Component
class InventoryTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    InventoryTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public String area() {
        return "inventory";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM inventory_reservations WHERE tenant_id = :tenantId
                """, params);
        removed += jdbc.update("""
                DELETE FROM inventory_stock WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
