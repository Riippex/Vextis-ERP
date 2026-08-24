package com.vextis.crm.infrastructure.persistence;

import com.vextis.crm.CustomerDirectory;
import com.vextis.crm.application.port.CustomerRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcCustomerRepository implements CustomerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCustomerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CustomerDirectory.CustomerSummary save(String tenantId, UUID id, String legalName, boolean active) {
        UUID customerId = id == null ? UUID.randomUUID() : id;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", customerId)
                .addValue("tenantId", tenantId)
                .addValue("legalName", legalName)
                .addValue("active", active);
        if (id == null) {
            customerId = jdbc.queryForObject(
                    """
                    INSERT INTO crm_customers (id, tenant_id, legal_name, active)
                    VALUES (:id, :tenantId, :legalName, :active)
                    ON CONFLICT (tenant_id, legal_name)
                    DO UPDATE SET active = EXCLUDED.active
                    RETURNING id
                    """,
                    parameters,
                    UUID.class
            );
            if (customerId == null) {
                throw new IllegalStateException("Customer save did not return an identifier");
            }
        } else {
            int updated = jdbc.update(
                    "UPDATE crm_customers SET legal_name = :legalName, active = :active "
                            + "WHERE tenant_id = :tenantId AND id = :id",
                    parameters
            );
            if (updated == 0) {
                throw new IllegalArgumentException("Customer was not found for tenant");
            }
        }
        return new CustomerDirectory.CustomerSummary(customerId, legalName, active);
    }
}
