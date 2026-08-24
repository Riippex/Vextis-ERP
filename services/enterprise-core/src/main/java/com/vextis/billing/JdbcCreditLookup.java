package com.vextis.billing;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcCreditLookup implements CreditLookup, CreditPortfolio {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcCreditLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CreditSnapshot> findByCustomer(String tenantId, UUID customerId) {
        return jdbc.query(
                """
                SELECT standing, max_payment_terms_days FROM billing_credit_profiles
                WHERE tenant_id = :tenantId AND customer_id = :customerId
                """,
                Map.of("tenantId", tenantId, "customerId", customerId),
                (rs, row) -> new CreditSnapshot(
                        CreditStanding.valueOf(rs.getString("standing")), rs.getInt("max_payment_terms_days"))
        ).stream().findFirst();
    }

    @Override
    public List<CreditProfileSummary> findAll(String tenantId) {
        return jdbc.query(
                """
                SELECT customer_id, standing, max_payment_terms_days FROM billing_credit_profiles
                WHERE tenant_id = :tenantId ORDER BY customer_id LIMIT 100
                """,
                Map.of("tenantId", tenantId),
                (rs, row) -> new CreditProfileSummary(
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("standing"),
                        rs.getInt("max_payment_terms_days"))
        );
    }
}
