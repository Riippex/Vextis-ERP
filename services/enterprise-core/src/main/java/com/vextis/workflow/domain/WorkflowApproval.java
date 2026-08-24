package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowApproval(
        UUID id,
        String recommendation,
        ApprovalStatus status,
        String requestedBy,
        Instant requestedAt,
        Instant expiresAt,
        String decidedBy,
        Instant decidedAt,
        String reason
) {
    public WorkflowApproval {
        if (id == null || status == null || requestedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Approval identity, status and timestamps are required");
        }
        if (recommendation == null || recommendation.isBlank() || recommendation.length() > 500
                || requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("Approval recommendation and requester are required");
        }
        if (!expiresAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("Approval expiration must be after its request time");
        }
        if (status == ApprovalStatus.PENDING && (decidedBy != null || decidedAt != null)) {
            throw new IllegalArgumentException("A pending approval cannot have decision metadata");
        }
        if (status != ApprovalStatus.PENDING && (decidedBy == null || decidedBy.isBlank() || decidedAt == null)) {
            throw new IllegalArgumentException("A decided approval requires decision metadata");
        }
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("Approval decision reason cannot exceed 500 characters");
        }
    }

    public WorkflowApproval decide(ApprovalDecision decision, String actorId, String decisionReason, Instant now) {
        if (decision == null) {
            throw new IllegalArgumentException("Approval decision is required");
        }
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval has already been decided");
        }
        if (!now.isBefore(expiresAt)) {
            throw new IllegalStateException("Approval has expired");
        }
        return new WorkflowApproval(
                id, recommendation,
                decision == ApprovalDecision.APPROVE ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED,
                requestedBy, requestedAt, expiresAt, actorId, now,
                decisionReason == null || decisionReason.isBlank() ? null : decisionReason.trim()
        );
    }
}
