package com.vextis.missioncontrol.api.graphql;

import com.vextis.billing.CreditPortfolio;
import com.vextis.crm.CustomerDirectory;
import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.ReservationDirectory;
import com.vextis.workflow.ExecutionOverview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@GraphQlTest(MissionControlGraphQlController.class)
class MissionControlGraphQlControllerTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private ExecutionOverview executions;

    @MockitoBean
    private CustomerDirectory customers;

    @MockitoBean
    private StockDirectory stock;

    @MockitoBean
    private ReservationDirectory reservations;

    @MockitoBean
    private CreditPortfolio credit;

    @Test
    void returnsTenantScopedOperationalOverview() {
        when(executions.findRecent("demo-tenant", 12)).thenReturn(List.of(new ExecutionOverview.ExecutionSummary(
                EXECUTION_ID, "PO-2026-001", "Acme Colombia", "RUNNING",
                "corr-001", Instant.parse("2026-08-24T17:00:00Z"))));
        when(customers.findAll("demo-tenant")).thenReturn(List.of(
                new CustomerDirectory.CustomerSummary(CUSTOMER_ID, "Acme Colombia", true)));
        when(stock.findAll("demo-tenant")).thenReturn(List.of(
                new StockDirectory.StockSummary("VXT-CHAIR-01", 40)));
        when(reservations.findAll("demo-tenant")).thenReturn(List.of());
        when(credit.findAll("demo-tenant")).thenReturn(List.of(
                new CreditPortfolio.CreditProfileSummary(CUSTOMER_ID, "GOOD", 30)));

        graphQlTester.document("""
                        query MissionControl {
                          missionControl {
                            executions { purchaseOrderNumber state }
                            customers { legalName active }
                            stockItems { sku availableQuantity }
                            creditProfiles { customerName standing maxPaymentTermsDays }
                          }
                        }
                        """)
                .execute()
                .path("missionControl.executions[0].state").entity(String.class).isEqualTo("RUNNING")
                .path("missionControl.customers[0].legalName").entity(String.class).isEqualTo("Acme Colombia")
                .path("missionControl.stockItems[0].availableQuantity").entity(Integer.class).isEqualTo(40)
                .path("missionControl.creditProfiles[0].standing").entity(String.class).isEqualTo("GOOD");
    }
}
