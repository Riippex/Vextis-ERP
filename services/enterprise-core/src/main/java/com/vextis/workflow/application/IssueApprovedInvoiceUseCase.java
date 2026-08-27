package com.vextis.workflow.application;

import com.vextis.billing.Invoice;

public interface IssueApprovedInvoiceUseCase {
    Invoice issueInvoice(IssueApprovedInvoiceCommand command);
}
