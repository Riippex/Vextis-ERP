package com.vextis.crm.application.port;

import com.vextis.crm.CustomerDirectory;

import java.util.UUID;

public interface CustomerRepository {

    CustomerDirectory.CustomerSummary save(String tenantId, UUID id, String legalName, boolean active);
}
