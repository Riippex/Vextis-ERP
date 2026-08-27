package com.vextis.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoiceIssuer {
    Invoice issue(IssueCommand command);

    record IssueCommand(
            String tenantId,
            String actorId,
            UUID orderId,
            UUID executionId,
            String customerName,
            String currency,
            List<LineInput> lines,
            int paymentTermsDays,
            String correlationId,
            String idempotencyKey
    ) {
    }

    record LineInput(String sku, int quantity, BigDecimal unitPrice) {
    }
}
