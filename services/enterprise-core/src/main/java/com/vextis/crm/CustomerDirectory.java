package com.vextis.crm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerDirectory {

    List<CustomerSummary> findAll(String tenantId);

    Optional<CustomerSummary> findById(String tenantId, UUID customerId);

    record CustomerSummary(UUID id, String legalName, boolean active) {
    }
}
