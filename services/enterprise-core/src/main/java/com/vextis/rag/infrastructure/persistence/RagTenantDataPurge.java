package com.vextis.rag.infrastructure.persistence;

import com.vextis.shared.TenantDataPurge;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes the tenant knowledge index. Chunks go with their document through
 * the cascade, but they are counted explicitly so the reset can report them.
 */
@Component
class RagTenantDataPurge implements TenantDataPurge {

    private final NamedParameterJdbcTemplate jdbc;

    RagTenantDataPurge(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String area() {
        return "knowledge";
    }

    @Override
    public int purgeTenant(String tenantId) {
        Map<String, Object> params = Map.of("tenantId", tenantId);
        int removed = 0;
        removed += jdbc.update("""
                DELETE FROM rag_document_chunks WHERE tenant_id = :tenantId
                """, params);
        removed += jdbc.update("""
                DELETE FROM rag_documents WHERE tenant_id = :tenantId
                """, params);
        return removed;
    }
}
