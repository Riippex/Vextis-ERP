package com.vextis.crm;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcCustomerLookup implements CustomerLookup, CustomerDirectory {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcCustomerLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CustomerSnapshot> findByLegalName(String tenantId, String legalName) {
        return jdbc.query(
                """
                SELECT id, legal_name, active FROM crm_customers
                WHERE tenant_id = :tenantId AND lower(legal_name) = lower(:legalName)
                """,
                Map.of("tenantId", tenantId, "legalName", legalName),
                (rs, row) -> new CustomerSnapshot(
                        rs.getObject("id", UUID.class), rs.getString("legal_name"), rs.getBoolean("active"))
        ).stream().findFirst();
    }

    @Override
    public List<CustomerSummary> findAll(String tenantId) {
        return jdbc.query(
                """
                SELECT id, legal_name, active FROM crm_customers
                WHERE tenant_id = :tenantId ORDER BY legal_name LIMIT 100
                """,
                Map.of("tenantId", tenantId),
                (rs, row) -> new CustomerSummary(
                        rs.getObject("id", UUID.class), rs.getString("legal_name"), rs.getBoolean("active"))
        );
    }

    @Override
    public Optional<CustomerSummary> findById(String tenantId, UUID customerId) {
        return jdbc.query(
                """
                SELECT id, legal_name, active FROM crm_customers
                WHERE tenant_id = :tenantId AND id = :customerId
                """,
                Map.of("tenantId", tenantId, "customerId", customerId),
                (rs, row) -> new CustomerSummary(
                        rs.getObject("id", UUID.class), rs.getString("legal_name"), rs.getBoolean("active"))
        ).stream().findFirst();
    }
}
