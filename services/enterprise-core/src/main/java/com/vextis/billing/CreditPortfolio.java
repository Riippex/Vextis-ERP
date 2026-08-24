package com.vextis.billing;

import java.util.List;
import java.util.UUID;

public interface CreditPortfolio {

    List<CreditProfileSummary> findAll(String tenantId);

    record CreditProfileSummary(UUID customerId, String standing, int maxPaymentTermsDays) {
    }
}
