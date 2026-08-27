package com.vextis.billing.application.port;

import com.vextis.billing.Invoice;
import com.vextis.billing.InvoiceIssuer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    void acquireLocks(String tenantId, UUID orderId, String idempotencyKey);

    Optional<Invoice> findByIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<Invoice> findByOrder(String tenantId, UUID orderId);

    Optional<Invoice> findById(String tenantId, UUID invoiceId);

    Optional<Invoice> findByExecution(String tenantId, UUID executionId);

    List<Invoice> findRecent(String tenantId, int limit);

    void save(Invoice invoice, InvoiceIssuer.IssueCommand command);
}
