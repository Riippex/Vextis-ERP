package com.vextis.billing.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes issued invoices and credit profiles. Invoice lines cascade from the
 * invoice.
 */
@Component
class BillingTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    BillingTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String area() {
        return "billing";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM billing_invoices WHERE tenant_id = :tenantId
                """, params);
        removed += jdbc.update("""
                DELETE FROM billing_credit_profiles WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
