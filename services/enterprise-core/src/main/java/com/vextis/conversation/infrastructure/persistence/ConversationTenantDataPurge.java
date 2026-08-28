package com.vextis.conversation.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes Ask Vextis conversations. Messages, agent activity and memory
 * evidence cascade from the conversation row.
 */
@Component
class ConversationTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    ConversationTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String area() {
        return "conversations";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM conversations WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
