package com.vextis.inventory.api.graphql;

import com.vextis.inventory.StockAdministration;
import com.vextis.inventory.StockDirectory;
import com.vextis.shared.security.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
class StockGraphQlController {

    private final StockAdministration stock;
    private final CurrentActorProvider actors;
    private final String demoTenantId;

    StockGraphQlController(
            StockAdministration stock,
            CurrentActorProvider actors,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.stock = stock;
        this.actors = actors;
        this.demoTenantId = demoTenantId;
    }

    @MutationMapping
    StockItemOverviewView setStockAvailability(@Argument @Valid SetStockAvailabilityInput input) {
        StockDirectory.StockSummary saved = stock.setAvailability(new StockAdministration.SetAvailabilityCommand(
                demoTenantId, actors.currentActorId(), input.sku(), input.availableQuantity()));
        return new StockItemOverviewView(saved.sku(), saved.availableQuantity());
    }

    record SetStockAvailabilityInput(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String sku,
            @Min(0) @Max(1_000_000) int availableQuantity
    ) {
    }

    record StockItemOverviewView(String sku, int availableQuantity) {
    }
}
