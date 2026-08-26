package com.vextis.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditTrail {

    void recordUserAction(UserAction action);

    void recordAgentDecision(AgentDecision decision);

    List<AuditRecord> findByCorrelation(String tenantId, String correlationId);

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

    record AuditRecord(
            UUID id,
            String correlationId,
            String actorType,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            String result,
            Instant occurredAt
    ) {
    }

    record AgentDecision(
            String tenantId,
            String correlationId,
            String agentId,
            String action,
            String resourceType,
            UUID resourceId,
            AgentDecisionResult result,
            Instant occurredAt
    ) {
    }

    enum AgentDecisionResult {
        DENIED
    }
}
