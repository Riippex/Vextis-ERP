package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record WorkflowExecution(
        UUID id,
        String tenantId,
        UUID sourceId,
        String goal,
        ExecutionState state,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        List<ExecutionTimelineEntry> timeline,
        WorkflowPlan plan,
        WorkflowReadiness readiness,
        WorkflowApproval approval
) {

    public WorkflowExecution(
            UUID id,
            String tenantId,
            UUID sourceId,
            String goal,
            ExecutionState state,
            String correlationId,
            Instant createdAt,
            Instant updatedAt,
            List<ExecutionTimelineEntry> timeline
    ) {
        this(id, tenantId, sourceId, goal, state, correlationId, createdAt, updatedAt, timeline, null, null, null);
    }

    public WorkflowExecution(
            UUID id, String tenantId, UUID sourceId, String goal, ExecutionState state,
            String correlationId, Instant createdAt, Instant updatedAt,
            List<ExecutionTimelineEntry> timeline, WorkflowPlan plan
    ) {
        this(id, tenantId, sourceId, goal, state, correlationId, createdAt, updatedAt, timeline, plan, null, null);
    }

    public WorkflowExecution(
            UUID id, String tenantId, UUID sourceId, String goal, ExecutionState state,
            String correlationId, Instant createdAt, Instant updatedAt,
            List<ExecutionTimelineEntry> timeline, WorkflowPlan plan, WorkflowReadiness readiness
    ) {
        this(id, tenantId, sourceId, goal, state, correlationId, createdAt, updatedAt,
                timeline, plan, readiness, null);
    }

    public WorkflowExecution {
        if (id == null || sourceId == null || state == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Execution identity, state and timestamps are required");
        }
        if (tenantId == null || tenantId.isBlank() || goal == null || goal.isBlank()
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Execution tenant, goal and correlation id are required");
        }
        timeline = List.copyOf(timeline);
    }

    public WorkflowExecution startPlanning(Instant now) {
        if (state != ExecutionState.RECEIVED) {
            throw new IllegalStateException("Only a received execution can start planning");
        }
        ArrayList<ExecutionTimelineEntry> updatedTimeline = new ArrayList<>(timeline);
        updatedTimeline.add(new ExecutionTimelineEntry(
                timeline.size() + 1,
                TimelineEntryType.STATUS_CHANGED,
                "Agent planning started",
                "Agent Runtime accepted the event and started planning the order.",
                now
        ));
        return new WorkflowExecution(
                id,
                tenantId,
                sourceId,
                goal,
                ExecutionState.PLANNING,
                correlationId,
                createdAt,
                now,
                updatedTimeline,
                plan,
                readiness,
                approval
        );
    }

    public WorkflowExecution recordPlan(WorkflowPlan structuredPlan, Instant now) {
        if (state != ExecutionState.PLANNING) {
            throw new IllegalStateException("Only a planning execution can record a plan");
        }
        if (structuredPlan == null) {
            throw new IllegalArgumentException("Structured plan is required");
        }
        ArrayList<ExecutionTimelineEntry> updatedTimeline = new ArrayList<>(timeline);
        updatedTimeline.add(new ExecutionTimelineEntry(
                timeline.size() + 1,
                TimelineEntryType.STATUS_CHANGED,
                "Structured plan recorded",
                "Gemini produced a validated plan with " + structuredPlan.steps().size() + " steps.",
                now
        ));
        return new WorkflowExecution(
                id,
                tenantId,
                sourceId,
                goal,
                ExecutionState.RUNNING,
                correlationId,
                createdAt,
                now,
                updatedTimeline,
                structuredPlan,
                readiness,
                approval
        );
    }

    public WorkflowExecution recordReadiness(WorkflowReadiness evaluation, Instant now) {
        if (state != ExecutionState.RUNNING || plan == null || plan.orderLines().isEmpty()) {
            throw new IllegalStateException("Only a running execution with extracted order lines can be evaluated");
        }
        if (readiness != null || evaluation == null) {
            throw new IllegalStateException("Readiness has already been evaluated or is missing");
        }
        ArrayList<ExecutionTimelineEntry> updatedTimeline = new ArrayList<>(timeline);
        updatedTimeline.add(new ExecutionTimelineEntry(
                timeline.size() + 1,
                TimelineEntryType.STATUS_CHANGED,
                "Read-only readiness evaluated",
                "CRM, inventory and billing checks were recorded without changing business state.",
                now
        ));
        return new WorkflowExecution(
                id, tenantId, sourceId, goal, state, correlationId, createdAt, now,
                updatedTimeline, plan, evaluation, approval
        );
    }

    public WorkflowExecution requestApproval(String recommendation, String requestedBy, Instant now, Instant expiresAt) {
        if (state != ExecutionState.RUNNING || readiness == null || approval != null) {
            throw new IllegalStateException("Only a ready running execution can request approval once");
        }
        String normalizedRecommendation = recommendation == null ? null : recommendation.trim();
        WorkflowApproval requested = new WorkflowApproval(
                UUID.randomUUID(), normalizedRecommendation, ApprovalStatus.PENDING,
                requestedBy, now, expiresAt, null, null, null);
        ArrayList<ExecutionTimelineEntry> updatedTimeline = new ArrayList<>(timeline);
        updatedTimeline.add(new ExecutionTimelineEntry(
                timeline.size() + 1, TimelineEntryType.APPROVAL_REQUESTED,
                "Human approval requested",
                "The agent recommendation and authoritative readiness evidence are awaiting a user decision.", now));
        return new WorkflowExecution(
                id, tenantId, sourceId, goal, ExecutionState.WAITING_APPROVAL, correlationId,
                createdAt, now, updatedTimeline, plan, readiness, requested);
    }

    public WorkflowExecution decideApproval(
            UUID approvalId, ApprovalDecision decision, String actorId, String reason, Instant now
    ) {
        if (state != ExecutionState.WAITING_APPROVAL || approval == null || !approval.id().equals(approvalId)) {
            throw new IllegalStateException("Execution is not waiting for this approval");
        }
        WorkflowApproval decided = approval.decide(decision, actorId, reason, now);
        ExecutionState nextState = decision == ApprovalDecision.APPROVE
                ? ExecutionState.RUNNING : ExecutionState.FAILED;
        ArrayList<ExecutionTimelineEntry> updatedTimeline = new ArrayList<>(timeline);
        updatedTimeline.add(new ExecutionTimelineEntry(
                timeline.size() + 1, TimelineEntryType.APPROVAL_DECIDED,
                decision == ApprovalDecision.APPROVE ? "Recommendation approved" : "Recommendation rejected",
                reason == null || reason.isBlank() ? "Decision recorded by an authenticated user." : reason.trim(), now));
        return new WorkflowExecution(
                id, tenantId, sourceId, goal, nextState, correlationId,
                createdAt, now, updatedTimeline, plan, readiness, decided);
    }
}
