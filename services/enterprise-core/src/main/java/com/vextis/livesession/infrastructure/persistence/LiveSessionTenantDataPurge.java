package com.vextis.livesession.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes issued Live voice session credentials so a reset cannot leave a
 * still-valid token behind.
 */
@Component
class LiveSessionTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    LiveSessionTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String area() {
        return "liveSessions";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM live_sessions WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
