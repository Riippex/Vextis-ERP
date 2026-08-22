package com.vextis.workflow.api.internal;

import com.vextis.workflow.application.StartPlanningCommand;
import com.vextis.workflow.application.StartPlanningUseCase;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentWorkflowToolController.class)
@Import(AgentToolAuthorizer.class)
class AgentWorkflowToolControllerTests {

    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");
    private static final UUID SOURCE_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StartPlanningUseCase startPlanning;

    @Test
    void authenticatedCoordinatorCanStartPlanning() throws Exception {
        when(startPlanning.startPlanning(any(StartPlanningCommand.class))).thenReturn(planningExecution());

        mockMvc.perform(validRequest("Bearer test-service-token", "demo-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.state").value("PLANNING"))
                .andExpect(jsonPath("$.correlationId").value("corr-001"));

        verify(startPlanning).startPlanning(any(StartPlanningCommand.class));
    }

    @Test
    void rejectsInvalidServiceCredentialBeforeCallingUseCase() throws Exception {
        mockMvc.perform(validRequest("Bearer wrong-token", "demo-tenant"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(startPlanning);
    }

    @Test
    void rejectsTenantOutsideConfiguredScope() throws Exception {
        mockMvc.perform(validRequest("Bearer test-service-token", "other-tenant"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(startPlanning);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(
            String authorization,
            String tenantId
    ) {
        return post("/internal/agent-tools/v1/workflows/{executionId}/planning", EXECUTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authorization)
                .header("X-Tenant-Id", tenantId)
                .header("X-Agent-Id", "coordinator-agent")
                .header("X-Correlation-Id", "corr-001")
                .header("Idempotency-Key", "8b962f0a-1850-4fcc-a6f5-97e45c67a16e")
                .content("""
                        {
                          "eventId": "8b962f0a-1850-4fcc-a6f5-97e45c67a16e",
                          "documentUri": "gs://vextis-demo/orders/po-2026-001.pdf"
                        }
                        """);
    }

    private WorkflowExecution planningExecution() {
        Instant now = Instant.parse("2026-08-21T03:30:02Z");
        return new WorkflowExecution(
                EXECUTION_ID,
                "demo-tenant",
                SOURCE_ID,
                "Process purchase order",
                ExecutionState.PLANNING,
                "corr-001",
                now.minusSeconds(2),
                now,
                List.of(
                        new ExecutionTimelineEntry(
                                1,
                                TimelineEntryType.RECEIVED,
                                "Order received",
                                "Ready for planning.",
                                now.minusSeconds(2)
                        ),
                        new ExecutionTimelineEntry(
                                2,
                                TimelineEntryType.STATUS_CHANGED,
                                "Agent planning started",
                                "Agent Runtime accepted the event.",
                                now
                        )
                )
        );
    }
}
