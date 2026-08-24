package com.vextis.workflow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowApprovalTests {

    private static final Instant NOW = Instant.parse("2026-08-24T19:00:00Z");

    @Test
    void approvalRequiresReadinessAndRecordsAuthenticatedDecision() {
        WorkflowExecution waiting = runningExecution().requestApproval(
                "Proceed after reviewing evidence.", "coordinator-agent", NOW, NOW.plusSeconds(3600));

        WorkflowExecution approved = waiting.decideApproval(
                waiting.approval().id(), ApprovalDecision.APPROVE, "firebase-user", "Evidence reviewed", NOW.plusSeconds(60));

        assertThat(waiting.state()).isEqualTo(ExecutionState.WAITING_APPROVAL);
        assertThat(approved.state()).isEqualTo(ExecutionState.RUNNING);
        assertThat(approved.approval().status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.approval().decidedBy()).isEqualTo("firebase-user");
        assertThat(approved.timeline().getLast().type()).isEqualTo(TimelineEntryType.APPROVAL_DECIDED);
    }

    @Test
    void expiredApprovalCannotBeDecided() {
        WorkflowExecution waiting = runningExecution().requestApproval(
                "Proceed after reviewing evidence.", "coordinator-agent", NOW, NOW.plusSeconds(60));

        assertThatThrownBy(() -> waiting.decideApproval(
                waiting.approval().id(), ApprovalDecision.REJECT, "firebase-user", null, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Approval has expired");
    }

    private WorkflowExecution runningExecution() {
        WorkflowPlan plan = new WorkflowPlan(
                "Process order", "gemini-3.5-flash", NOW.minusSeconds(120),
                List.of(new WorkflowPlanStep(1, PlanningDepartment.CRM_SALES, "Validate customer", true)),
                List.of(new ExtractedOrderLine("VXT-CHAIR-01", 10)), 30);
        WorkflowReadiness readiness = new WorkflowReadiness(
                NOW.minusSeconds(30),
                List.of(
                        new WorkflowReadinessCheck(PlanningDepartment.CRM_SALES, ReadinessStatus.READY, "Ready"),
                        new WorkflowReadinessCheck(PlanningDepartment.INVENTORY_OPERATIONS, ReadinessStatus.READY, "Ready"),
                        new WorkflowReadinessCheck(PlanningDepartment.FINANCE_BILLING, ReadinessStatus.READY, "Ready")));
        return new WorkflowExecution(
                UUID.randomUUID(), "demo-tenant", UUID.randomUUID(), "Process order", ExecutionState.RUNNING,
                "corr-001", NOW.minusSeconds(180), NOW.minusSeconds(30),
                List.of(new ExecutionTimelineEntry(
                        1, TimelineEntryType.STATUS_CHANGED, "Ready", "Evidence recorded", NOW.minusSeconds(30))),
                plan, readiness);
    }
}
