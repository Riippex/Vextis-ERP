package com.vextis.billing.application;

import com.vextis.audit.AuditTrail;
import com.vextis.billing.CreditAdministration;
import com.vextis.billing.CreditLookup;
import com.vextis.billing.CreditPortfolio;
import com.vextis.billing.application.port.CreditRepository;
import com.vextis.crm.CustomerDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class CreditManagementService implements CreditAdministration {

    private final CreditRepository credit;
    private final CustomerDirectory customers;
    private final AuditTrail audit;
    private final Clock clock;

    CreditManagementService(CreditRepository credit, CustomerDirectory customers, AuditTrail audit, Clock clock) {
        this.credit = credit;
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SavedCreditProfile save(SaveCreditProfileCommand command) {
        String tenantId = requireText(command.tenantId(), "Tenant id", 100);
        String actorId = requireText(command.actorId(), "Actor id", 150);
        if (command.customerId() == null || command.standing() == null) {
            throw new IllegalArgumentException("Customer and credit standing are required");
        }
        if (command.maxPaymentTermsDays() < 0 || command.maxPaymentTermsDays() > 365) {
            throw new IllegalArgumentException("Maximum payment terms must be between 0 and 365 days");
        }
        CustomerDirectory.CustomerSummary customer = customers.findById(tenantId, command.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer was not found for tenant"));
        CreditPortfolio.CreditProfileSummary saved = credit.save(
                tenantId, command.customerId(), command.standing(), command.maxPaymentTermsDays());
        audit.recordUserAction(new AuditTrail.UserAction(
                tenantId,
                UUID.randomUUID().toString(),
                actorId,
                "billing.credit-profile.saved",
                "CreditProfile",
                command.customerId(),
                clock.instant()
        ));
        return new SavedCreditProfile(
                saved.customerId(),
                customer.legalName(),
                CreditLookup.CreditStanding.valueOf(saved.standing()),
                saved.maxPaymentTermsDays()
        );
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }
}
