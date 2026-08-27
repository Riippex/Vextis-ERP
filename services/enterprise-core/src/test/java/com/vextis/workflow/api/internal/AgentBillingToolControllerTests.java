package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.billing.Invoice;
import com.vextis.workflow.application.IssueApprovedInvoiceCommand;
import com.vextis.workflow.application.IssueApprovedInvoiceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

@WebMvcTest(AgentBillingToolController.class)
@Import(AgentToolAuthorizer.class)
class AgentBillingToolControllerTests {
    private static final UUID ORDER_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueApprovedInvoiceUseCase issueInvoice;

    @MockitoBean
    private AgentDirectory agents;

    @MockitoBean
    private AgentAuthorizationDenialRecorder denialRecorder;

    @BeforeEach
    void authorizeBillingAgent() {
        when(agents.findActive("demo-tenant", "vextis_billing_agent")).thenReturn(Optional.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_billing_agent", "1.0.0", "Billing Agent", "FINANCE_BILLING", "purpose",
                        "GOOGLE_ADK", "gemini-3.5-flash", "1.0.0", "coordinator-agent", "ACTIVE",
                        List.of(), List.of("get_credit", "create_invoice"))));
    }

    @Test
    void authenticatedBillingAgentCanIssueApprovedInvoice() throws Exception {
        when(issueInvoice.issueInvoice(any(IssueApprovedInvoiceCommand.class))).thenReturn(invoice());

        mockMvc.perform(validRequest("Bearer test-service-token", "demo-tenant"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.total").value("1190.00"))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        verify(issueInvoice).issueInvoice(org.mockito.ArgumentMatchers.argThat(command ->
                command.tenantId().equals("demo-tenant")
                        && command.actor().id().equals("vextis_billing_agent")
                        && command.executionId().equals(EXECUTION_ID)));
    }

    @Test
    void rejectsUnauthorizedAgentBeforeInvoiceUseCase() throws Exception {
        mockMvc.perform(validRequest("Bearer wrong-token", "demo-tenant"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(issueInvoice);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(
            String authorization, String tenantId
    ) {
        return post("/internal/agent-tools/v1/billing/orders/{orderId}/invoice", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authorization)
                .header("X-Tenant-Id", tenantId)
                .header("X-Agent-Id", "vextis_billing_agent")
                .header("X-Correlation-Id", "corr-001")
                .header("Idempotency-Key", "95a0c3b0-e693-4a44-a396-147453bbbf02:issue-invoice")
                .content("{\"executionId\":\"" + EXECUTION_ID + "\"}");
    }

    private Invoice invoice() {
        return new Invoice(
                UUID.fromString("3e2fb128-12e8-48fa-acdd-4748e00657ef"), ORDER_ID, EXECUTION_ID,
                "Acme Colombia", "COP", new BigDecimal("1000.00"), new BigDecimal("190.00"),
                new BigDecimal("1190.00"), Invoice.Status.ISSUED, 30,
                Instant.parse("2026-08-27T18:00:00Z"), "corr-001",
                List.of(new Invoice.Line(
                        "VXT-CHAIR-01", 10, new BigDecimal("100.00"), new BigDecimal("1000.00"))));
    }
}
