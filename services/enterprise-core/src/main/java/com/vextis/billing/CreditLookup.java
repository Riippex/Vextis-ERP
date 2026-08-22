package com.vextis.billing;

import java.util.Optional;
import java.util.UUID;

public interface CreditLookup {
    Optional<CreditSnapshot> findByCustomer(String tenantId, UUID customerId);

    record CreditSnapshot(CreditStanding standing, int maxPaymentTermsDays) {
    }

    enum CreditStanding {
        GOOD, REVIEW, BLOCKED
    }
}
