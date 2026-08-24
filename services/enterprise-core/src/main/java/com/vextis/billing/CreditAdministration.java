package com.vextis.billing;

import java.util.UUID;

public interface CreditAdministration {

    SavedCreditProfile save(SaveCreditProfileCommand command);

    record SaveCreditProfileCommand(
            String tenantId,
            String actorId,
            UUID customerId,
            CreditLookup.CreditStanding standing,
            int maxPaymentTermsDays
    ) {
    }

    record SavedCreditProfile(
            UUID customerId,
            String customerName,
            CreditLookup.CreditStanding standing,
            int maxPaymentTermsDays
    ) {
    }
}
