package com.vextis.audit;

import java.time.Instant;
import java.util.UUID;

public interface AuditTrail {

    void recordUserAction(UserAction action);

    record UserAction(
            String tenantId,
            String correlationId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Instant occurredAt
    ) {
    }
}
