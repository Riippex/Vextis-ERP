package com.vextis.workflow.infrastructure.persistence;

import com.vextis.crm.QuoteExecutionLookup;
import com.vextis.workflow.application.port.PurchaseOrderWorkflowRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class WorkflowQuoteExecutionLookup implements QuoteExecutionLookup {

    private final PurchaseOrderWorkflowRepository repository;

    WorkflowQuoteExecutionLookup(PurchaseOrderWorkflowRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<QuoteExecution> findQuote(String tenantId, UUID quoteId) {
        return repository.findExecution(tenantId, quoteId)
                .map(exec -> new QuoteExecution(exec.id(), exec.tenantId(), exec.correlationId()));
    }
}
