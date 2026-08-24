package com.vextis.crm.application;

import com.vextis.audit.AuditTrail;
import com.vextis.crm.CustomerAdministration;
import com.vextis.crm.CustomerDirectory;
import com.vextis.crm.application.port.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class CustomerManagementService implements CustomerAdministration {

    private final CustomerRepository customers;
    private final AuditTrail audit;
    private final Clock clock;

    CustomerManagementService(CustomerRepository customers, AuditTrail audit, Clock clock) {
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CustomerDirectory.CustomerSummary save(SaveCustomerCommand command) {
        String tenantId = requireText(command.tenantId(), "Tenant id", 100);
        String actorId = requireText(command.actorId(), "Actor id", 150);
        String legalName = requireText(command.legalName(), "Legal name", 200);
        CustomerDirectory.CustomerSummary saved = customers.save(
                tenantId, command.id(), legalName, command.active());
        audit.recordUserAction(new AuditTrail.UserAction(
                tenantId,
                UUID.randomUUID().toString(),
                actorId,
                "crm.customer.saved",
                "Customer",
                saved.id(),
                clock.instant()
        ));
        return saved;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }
}
