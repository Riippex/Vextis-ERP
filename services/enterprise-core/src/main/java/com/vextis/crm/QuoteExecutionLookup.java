package com.vextis.crm;

import java.util.Optional;
import java.util.UUID;

public interface QuoteExecutionLookup {

    record QuoteExecution(UUID id, String tenantId, String correlationId) {
    }

    Optional<QuoteExecution> findQuote(String tenantId, UUID quoteId);
}
