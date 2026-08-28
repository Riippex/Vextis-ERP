package com.vextis.workflow.api.internal;

import com.vextis.shared.ConfiguredServiceCallerIdentities;
import com.vextis.agentregistry.AgentDirectory;
import com.vextis.inventory.StockReservation;
import com.vextis.workflow.application.ReserveApprovedStockCommand;
import com.vextis.workflow.application.ReserveApprovedStockUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentInventoryToolController.class)
@Import({AgentToolAuthorizer.class, ConfiguredServiceCallerIdentities.class})
class AgentInventoryToolControllerTests {

    private static final UUID ORDER_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReserveApprovedStockUseCase reserveStock;

    @MockitoBean
    private AgentDirectory agents;

    @BeforeEach
    void authorizeInventoryAgent() {
        when(agents.findActive("demo-tenant", "vextis_inventory_agent")).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_inventory_agent", "1.0.0", "Inventory Agent",
                        "INVENTORY_OPERATIONS", "purpose", "GOOGLE_ADK", "gemini-3.5-flash",
                        "1.0.0", "coordinator-agent", "ACTIVE", List.of(), List.of("reserve_stock"))));
    }

    @Test
    void authenticatedCoordinatorCanReserveApprovedStock() throws Exception {
        when(reserveStock.reserve(any(ReserveApprovedStockCommand.class))).thenReturn(
                new StockReservation.Reservation(
                        UUID.fromString("f47c82aa-9739-4b55-9c7f-0950a9218e1d"),
                        ORDER_ID,
                        "VXT-CHAIR-01",
                        10,
                        StockReservation.Status.RESERVED,
                        Instant.parse("2026-08-24T20:00:01Z")));

        mockMvc.perform(validRequest("Bearer test-service-token", "demo-tenant"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.sku").value("VXT-CHAIR-01"))
                .andExpect(jsonPath("$.status").value("RESERVED"));

        verify(reserveStock).reserve(org.mockito.ArgumentMatchers.argThat(command ->
                command.tenantId().equals("demo-tenant")
                        && command.actor().id().equals("vextis_inventory_agent")
                        && command.correlationId().equals("corr-001")
                        && command.idempotencyKey().endsWith(":reserve:VXT-CHAIR-01")));
    }

    @Test
    void rejectsInvalidServiceCredentialBeforeReservation() throws Exception {
        mockMvc.perform(validRequest("Bearer wrong-token", "demo-tenant"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reserveStock);
    }

    @Test
    void rejectsTenantOutsideConfiguredScopeBeforeReservation() throws Exception {
        mockMvc.perform(validRequest("Bearer test-service-token", "other-tenant"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reserveStock);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(
            String authorization,
            String tenantId
    ) {
        return post("/internal/agent-tools/v1/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authorization)
                .header("X-Tenant-Id", tenantId)
                .header("X-Agent-Id", "vextis_inventory_agent")
                .header("X-Correlation-Id", "corr-001")
                .header("Idempotency-Key", "95a0c3b0-e693-4a44-a396-147453bbbf02:reserve:VXT-CHAIR-01")
                .content("""
                        {"orderId":"77cc63cc-3c91-4d80-a918-605b7f231cf8","sku":"VXT-CHAIR-01","quantity":10}
                        """);
    }
}
