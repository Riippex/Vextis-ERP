package com.vextis.workflow.api.internal;

import com.vextis.billing.CreditLookup;
import com.vextis.crm.CustomerLookup;
import com.vextis.inventory.StockLookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentBusinessReadToolController.class)
@Import(AgentToolAuthorizer.class)
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

    @Test
    void authenticatedCoordinatorCanReadTenantScopedCustomer() throws Exception {
        when(customers.findByLegalName("demo-tenant", "Acme Colombia"))
                .thenReturn(Optional.of(new CustomerLookup.CustomerSnapshot(
                        CUSTOMER_ID, "Acme Colombia", true)));

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/crm/customers/lookup")
                        .queryParam("legalName", "Acme Colombia")))
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

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/inventory/stock/VXT-CHAIR-01")))
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
                        CUSTOMER_ID)))
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

        mockMvc.perform(authorize(get("/internal/agent-tools/v1/inventory/stock/UNKNOWN-SKU")))
                .andExpect(status().isNotFound());

        verify(stock).findBySku("demo-tenant", "UNKNOWN-SKU");
    }

    @Test
    void rejectsUnauthorizedTenantBeforeAnyLookup() throws Exception {
        mockMvc.perform(get("/internal/agent-tools/v1/inventory/stock/VXT-CHAIR-01")
                        .header("Authorization", "Bearer test-service-token")
                        .header("X-Tenant-Id", "other-tenant")
                        .header("X-Agent-Id", "coordinator-agent")
                        .header("X-Correlation-Id", "corr-001"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customers, stock, credit);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request
                .header("Authorization", "Bearer test-service-token")
                .header("X-Tenant-Id", "demo-tenant")
                .header("X-Agent-Id", "coordinator-agent")
                .header("X-Correlation-Id", "corr-001");
    }
}
