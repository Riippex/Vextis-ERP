package com.vextis.crm.api.graphql;

import com.vextis.crm.CustomerAdministration;
import com.vextis.crm.CustomerDirectory;
import com.vextis.shared.security.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
class CustomerGraphQlController {

    private final CustomerAdministration customers;
    private final CurrentActorProvider actors;
    private final String demoTenantId;

    CustomerGraphQlController(
            CustomerAdministration customers,
            CurrentActorProvider actors,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.customers = customers;
        this.actors = actors;
        this.demoTenantId = demoTenantId;
    }

    @MutationMapping
    CustomerOverviewView upsertCustomer(@Argument @Valid UpsertCustomerInput input) {
        CustomerDirectory.CustomerSummary saved = customers.save(new CustomerAdministration.SaveCustomerCommand(
                demoTenantId, actors.currentActorId(), input.id(), input.legalName(), input.active()));
        return new CustomerOverviewView(saved.id(), saved.legalName(), saved.active());
    }

    record UpsertCustomerInput(UUID id, @NotBlank @Size(max = 200) String legalName, boolean active) {
    }

    record CustomerOverviewView(UUID id, String legalName, boolean active) {
    }
}
