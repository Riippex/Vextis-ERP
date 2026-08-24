package com.vextis.billing.infrastructure.persistence;

import com.vextis.billing.CreditLookup;
import com.vextis.billing.CreditPortfolio;
import com.vextis.billing.application.port.CreditRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcCreditRepository implements CreditRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCreditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CreditPortfolio.CreditProfileSummary save(
            String tenantId,
            UUID customerId,
            CreditLookup.CreditStanding standing,
            int maxPaymentTermsDays
    ) {
        jdbc.update(
                """
                INSERT INTO billing_credit_profiles
                    (tenant_id, customer_id, standing, max_payment_terms_days)
                VALUES (:tenantId, :customerId, :standing, :maxPaymentTermsDays)
                ON CONFLICT (tenant_id, customer_id)
                DO UPDATE SET standing = EXCLUDED.standing,
                              max_payment_terms_days = EXCLUDED.max_payment_terms_days
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("customerId", customerId)
                        .addValue("standing", standing.name())
                        .addValue("maxPaymentTermsDays", maxPaymentTermsDays)
        );
        return new CreditPortfolio.CreditProfileSummary(
                customerId, standing.name(), maxPaymentTermsDays);
    }
}
