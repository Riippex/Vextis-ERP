package com.vextis.crm;

import java.util.List;
import java.util.UUID;

public interface CustomerDirectory {

    List<CustomerSummary> findAll(String tenantId);

    record CustomerSummary(UUID id, String legalName, boolean active) {
    }
}
