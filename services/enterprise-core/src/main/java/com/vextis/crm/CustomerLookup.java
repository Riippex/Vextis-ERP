package com.vextis.crm;

import java.util.Optional;
import java.util.UUID;

public interface CustomerLookup {
    Optional<CustomerSnapshot> findByLegalName(String tenantId, String legalName);

    record CustomerSnapshot(UUID id, String legalName, boolean active) {
    }
}
