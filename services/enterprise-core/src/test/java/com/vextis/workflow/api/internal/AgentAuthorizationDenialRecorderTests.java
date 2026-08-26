package com.vextis.workflow.api.internal;

import com.vextis.audit.AuditTrail;
import com.vextis.workflow.application.FindExecutionUseCase;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAuthorizationDenialRecorderTests {

    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");
    private static final UUID SOURCE_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
    private static final Instant NOW = Instant.parse("2026-08-26T18:00:00Z");

    private final FindExecutionUseCase findExecution = mock(FindExecutionUseCase.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final AgentAuthorizationDenialRecorder recorder = new AgentAuthorizationDenialRecorder(
            findExecution, auditTrail, Clock.fixed(NOW, ZoneOffset.UTC), "demo-tenant");

    @Test
    void recordsDeniedAttemptOnlyAfterBindingItToTheExistingExecution() {
        when(findExecution.findById("demo-tenant", EXECUTION_ID)).thenReturn(Optional.of(execution()));

        recorder.recordSafely(
                "demo-tenant", "rogue-agent", "corr-001", EXECUTION_ID,
                AgentAuthorizationDenialRecorder.WorkflowTool.START_EXECUTION_PLANNING);

        ArgumentCaptor<AuditTrail.AgentDecision> decision =
                ArgumentCaptor.forClass(AuditTrail.AgentDecision.class);
        verify(auditTrail).recordAgentDecision(decision.capture());
        assertThat(decision.getValue().tenantId()).isEqualTo("demo-tenant");
        assertThat(decision.getValue().correlationId()).isEqualTo("corr-001");
        assertThat(decision.getValue().agentId()).isEqualTo("rogue-agent");
        assertThat(decision.getValue().action()).isEqualTo("START_EXECUTION_PLANNING");
        assertThat(decision.getValue().resourceId()).isEqualTo(EXECUTION_ID);
        assertThat(decision.getValue().result()).isEqualTo(AuditTrail.AgentDecisionResult.DENIED);
        assertThat(decision.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void ignoresUntrustedTenantBeforeLookingUpAnExecution() {
        recorder.recordSafely(
                "other-tenant", "rogue-agent", "corr-001", EXECUTION_ID,
                AgentAuthorizationDenialRecorder.WorkflowTool.START_EXECUTION_PLANNING);

        verifyNoInteractions(findExecution, auditTrail);
    }

    @Test
    void ignoresCorrelationThatDoesNotBelongToTheExecution() {
        when(findExecution.findById("demo-tenant", EXECUTION_ID)).thenReturn(Optional.of(execution()));

        recorder.recordSafely(
                "demo-tenant", "rogue-agent", "forged-correlation", EXECUTION_ID,
                AgentAuthorizationDenialRecorder.WorkflowTool.START_EXECUTION_PLANNING);

        verifyNoInteractions(auditTrail);
    }

    private WorkflowExecution execution() {
        return new WorkflowExecution(
                EXECUTION_ID,
                "demo-tenant",
                SOURCE_ID,
                "Process purchase order",
                ExecutionState.RECEIVED,
                "corr-001",
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                List.of()
        );
    }
}
