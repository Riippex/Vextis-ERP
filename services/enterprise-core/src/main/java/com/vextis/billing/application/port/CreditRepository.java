package com.vextis.billing.application.port;

import com.vextis.billing.CreditLookup;
import com.vextis.billing.CreditPortfolio;

import java.util.UUID;

public interface CreditRepository {

    CreditPortfolio.CreditProfileSummary save(
            String tenantId,
            UUID customerId,
            CreditLookup.CreditStanding standing,
            int maxPaymentTermsDays
    );
}
