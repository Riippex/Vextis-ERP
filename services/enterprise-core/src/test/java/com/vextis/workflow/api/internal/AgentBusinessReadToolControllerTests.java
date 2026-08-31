package com.vextis.workflow.api.internal;

import com.vextis.shared.ConfiguredServiceCallerIdentities;
import com.vextis.agentregistry.AgentDirectory;
import com.vextis.billing.CreditLookup;
import com.vextis.crm.CustomerLookup;
import com.vextis.crm.CustomerDirectory;
import com.vextis.inventory.StockLookup;
import com.vextis.inventory.StockDirectory;
import com.vextis.workflow.ExecutionOverview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentBusinessReadToolController.class)
@Import({AgentToolAuthorizer.class, ConfiguredServiceCallerIdentities.class})
class AgentBusinessReadToolControllerTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("09ec135d-9688-47de-ac71-5b8420b97488");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerLookup customers;

    @MockitoBean
    private StockLookup stock;

    @MockitoBean
    private CreditLookup credit;

    @MockitoBean
    private CustomerDirectory customerDirectory;

    @MockitoBean
    private StockDirectory stockDirectory;

    @MockitoBean
    private ExecutionOverview executions;

    @MockitoBean
    private AgentDirectory agents;

    @BeforeEach
    void authorizeRegisteredSpecialists() {
        allow("vextis_crm_agent", "lookup_customer", "list_customers", "search_customer_orders");
        allow("vextis_inventory_agent", "get_stock", "search_inventory");
        allow("vextis_billing_agent", "get_credit");
    }

    @Test
    void listsBoundedTenantCustomers() throws Exception {
        when(customerDirectory.findAll("demo-tenant")).thenReturn(List.of(
                new CustomerDirectory.CustomerSummary(CUSTOMER_ID, "Acme Colombia", true)));

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/crm/customers")
                        .queryParam("limit", "10"), "vextis_crm_agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].legalName").value("Acme Colombia"));

        verify(customerDirectory).findAll("demo-tenant");
    }

    @Test
    void returnsAuthoritativeCustomerOrderCountAndRecentOrders() throws Exception {
        UUID executionId = UUID.fromString("6e9aa9f4-113b-4a81-8f1f-55cd792fc711");
        when(executions.findCustomerOrders("demo-tenant", "Acme Colombia", 20))
                .thenReturn(new ExecutionOverview.CustomerOrders(7, List.of(
                        new ExecutionOverview.ExecutionSummary(executionId, "PO-2026-007", "Acme Colombia",
                                "RUNNING", "corr-001", Instant.parse("2026-08-31T15:47:00Z")))));

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/crm/customers/orders")
                        .queryParam("legalName", "Acme Colombia"), "vextis_crm_agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(7))
                .andExpect(jsonPath("$.orders[0].purchaseOrderNumber").value("PO-2026-007"));
    }

    @Test
    void searchesOrListsBoundedTenantInventory() throws Exception {
        when(stockDirectory.findAll("demo-tenant")).thenReturn(List.of(
                new StockDirectory.StockSummary("VXT-CHAIR-01", 40),
                new StockDirectory.StockSummary("VXT-DESK-01", 12)));

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/inventory/stock")
                        .queryParam("query", "desk"), "vextis_inventory_agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("VXT-DESK-01"))
                .andExpect(jsonPath("$[0].availableQuantity").value(12));

        verify(stockDirectory).findAll("demo-tenant");
    }

    @Test
    void authenticatedCoordinatorCanReadTenantScopedCustomer() throws Exception {
        when(customers.findByLegalName("demo-tenant", "Acme Colombia"))
                .thenReturn(Optional.of(new CustomerLookup.CustomerSnapshot(
                        CUSTOMER_ID, "Acme Colombia", true)));

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/crm/customers/lookup")
                        .queryParam("legalName", "Acme Colombia"), "vextis_crm_agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.legalName").value("Acme Colombia"))
                .andExpect(jsonPath("$.active").value(true));

        verify(customers).findByLegalName("demo-tenant", "Acme Colombia");
        verifyNoInteractions(stock, credit);
    }

    @Test
    void authenticatedCoordinatorCanReadTenantScopedStock() throws Exception {
        when(stock.findBySku("demo-tenant", "VXT-CHAIR-01"))
                .thenReturn(Optional.of(new StockLookup.StockSnapshot("VXT-CHAIR-01", 40)));

        mockMvc.perform(authorize(
                        get("/internal/agent-tools/v1/inventory/stock/VXT-CHAIR-01"),
                        "vextis_inventory_agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("VXT-CHAIR-01"))
                .andExpect(jsonPath("$.availableQuantity").value(40));

        verify(stock).findBySku("demo-tenant", "VXT-CHAIR-01");
        verifyNoInteractions(customers, credit);
    }

    @Test
    void authenticatedCoordinatorCanReadTenantScopedCredit() throws Exception {
        when(credit.findByCustomer("demo-tenant", CUSTOMER_ID))
                .thenReturn(Optional.of(new CreditLookup.CreditSnapshot(
                        CreditLookup.CreditStanding.GOOD, 30)));

        mockMvc.perform(authorize(get(
                        "/internal/agent-tools/v1/billing/customers/{customerId}/credit",
                        CUSTOMER_ID), "vextis_billing_agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.standing").value("GOOD"))
                .andExpect(jsonPath("$.maxPaymentTermsDays").value(30));

        verify(credit).findByCustomer("demo-tenant", CUSTOMER_ID);
        verifyNoInteractions(customers, stock);
    }

    @Test
    void returnsNotFoundWithoutLeakingAnotherTenant() throws Exception {
        when(stock.findBySku("demo-tenant", "UNKNOWN-SKU")).thenReturn(Optional.empty());

        mockMvc.perform(authorize(
                        get("/internal/agent-tools/v1/inventory/stock/UNKNOWN-SKU"),
                        "vextis_inventory_agent"))
                .andExpect(status().isNotFound());

        verify(stock).findBySku("demo-tenant", "UNKNOWN-SKU");
    }

    @Test
    void rejectsUnauthorizedTenantBeforeAnyLookup() throws Exception {
        mockMvc.perform(get("/internal/agent-tools/v1/inventory/stock/VXT-CHAIR-01")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "other-tenant")
                        .header("X-Agent-Id", "vextis_inventory_agent")
                        .header("X-Correlation-Id", "corr-001"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customers, stock, credit);
    }

    @Test
    void rejectsCrossDepartmentToolBeforeAnyLookup() throws Exception {
        mockMvc.perform(authorize(
                        get("/internal/agent-tools/v1/inventory/stock/VXT-CHAIR-01"),
                        "vextis_crm_agent"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customers, stock, credit);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String agentId
    ) {
        return request
                .header("Authorization", "Bearer test-service-token")
                .header("X-Tenant-Id", "demo-tenant")
                .header("X-Agent-Id", agentId)
                .header("X-Correlation-Id", "corr-001");
    }

    private void allow(String agentId, String... tools) {
        when(agents.findActive("demo-tenant", agentId)).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        agentId, "1.0.0", agentId, "CROSS_DEPARTMENT", "purpose", "GOOGLE_ADK",
                        "gemini-3.5-flash", "1.0.0", "coordinator-agent", "ACTIVE",
                        List.of(), List.of(tools))));
    }
}
