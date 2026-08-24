package com.vextis.inventory.api.graphql;

import com.vextis.inventory.StockAdministration;
import com.vextis.inventory.StockDirectory;
import com.vextis.shared.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(StockGraphQlController.class)
class StockGraphQlControllerTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private StockAdministration stock;

    @MockitoBean
    private CurrentActorProvider actors;

    @Test
    void setsTenantScopedStockAvailability() {
        when(actors.currentActorId()).thenReturn("firebase-user-123");
        when(stock.setAvailability(any())).thenReturn(new StockDirectory.StockSummary("VXT-CHAIR-01", 40));

        graphQlTester.document("""
                        mutation SetStock($input: SetStockAvailabilityInput!) {
                          setStockAvailability(input: $input) { sku availableQuantity }
                        }
                        """)
                .variable("input", Map.of("sku", "VXT-CHAIR-01", "availableQuantity", 40))
                .execute()
                .path("setStockAvailability.sku").entity(String.class).isEqualTo("VXT-CHAIR-01")
                .path("setStockAvailability.availableQuantity").entity(Integer.class).isEqualTo(40);

        ArgumentCaptor<StockAdministration.SetAvailabilityCommand> command =
                ArgumentCaptor.forClass(StockAdministration.SetAvailabilityCommand.class);
        verify(stock).setAvailability(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("demo-tenant");
        assertThat(command.getValue().actorId()).isEqualTo("firebase-user-123");
    }
}
