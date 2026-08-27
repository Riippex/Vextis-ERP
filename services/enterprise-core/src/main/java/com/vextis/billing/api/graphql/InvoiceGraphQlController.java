package com.vextis.billing.api.graphql;

import com.vextis.billing.InvoiceDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
class InvoiceGraphQlController {
    private final InvoiceDirectory invoices;
    private final String demoTenantId;

    InvoiceGraphQlController(
            InvoiceDirectory invoices,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.invoices = invoices;
        this.demoTenantId = demoTenantId;
    }

    @QueryMapping
    InvoiceView invoice(@Argument UUID id) {
        return invoices.findById(demoTenantId, id).map(InvoiceView::from).orElse(null);
    }
}
