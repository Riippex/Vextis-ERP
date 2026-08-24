package com.vextis.billing.api.graphql;

import com.vextis.billing.CreditAdministration;
import com.vextis.billing.CreditLookup;
import com.vextis.shared.security.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
class CreditGraphQlController {

    private final CreditAdministration credit;
    private final CurrentActorProvider actors;
    private final String demoTenantId;

    CreditGraphQlController(
            CreditAdministration credit,
            CurrentActorProvider actors,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.credit = credit;
        this.actors = actors;
        this.demoTenantId = demoTenantId;
    }

    @MutationMapping
    CreditProfileOverviewView upsertCreditProfile(@Argument @Valid UpsertCreditProfileInput input) {
        CreditAdministration.SavedCreditProfile saved = credit.save(
                new CreditAdministration.SaveCreditProfileCommand(
                        demoTenantId,
                        actors.currentActorId(),
                        input.customerId(),
                        input.standing(),
                        input.maxPaymentTermsDays()
                ));
        return new CreditProfileOverviewView(
                saved.customerId(), saved.customerName(), saved.standing().name(), saved.maxPaymentTermsDays());
    }

    record UpsertCreditProfileInput(
            @NotNull UUID customerId,
            @NotNull CreditLookup.CreditStanding standing,
            @Min(0) @Max(365) int maxPaymentTermsDays
    ) {
    }

    record CreditProfileOverviewView(
            UUID customerId,
            String customerName,
            String standing,
            int maxPaymentTermsDays
    ) {
    }
}
