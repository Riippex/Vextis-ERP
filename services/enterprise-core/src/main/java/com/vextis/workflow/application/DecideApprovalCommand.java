package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ApprovalDecision;
import java.util.UUID;

public record DecideApprovalCommand(
        String tenantId, Actor actor, UUID executionId, UUID approvalId,
        ApprovalDecision decision, String reason, String idempotencyKey
) {
}
