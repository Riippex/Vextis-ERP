package com.vextis.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceDirectory {
    Optional<Invoice> findById(String tenantId, UUID invoiceId);

    Optional<Invoice> findByExecution(String tenantId, UUID executionId);

    List<Invoice> findRecent(String tenantId, int limit);
}
