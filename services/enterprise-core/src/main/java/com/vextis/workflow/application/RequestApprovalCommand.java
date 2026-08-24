package com.vextis.workflow.application;

import com.vextis.workflow.domain.Actor;
import java.util.UUID;

public record RequestApprovalCommand(
        String tenantId, Actor actor, UUID executionId, String correlationId,
        String recommendation, String idempotencyKey
) {
}
