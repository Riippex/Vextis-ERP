package com.vextis.crm;

import java.util.UUID;

public interface CustomerAdministration {

    CustomerDirectory.CustomerSummary save(SaveCustomerCommand command);

    record SaveCustomerCommand(String tenantId, String actorId, UUID id, String legalName, boolean active) {
    }
}
