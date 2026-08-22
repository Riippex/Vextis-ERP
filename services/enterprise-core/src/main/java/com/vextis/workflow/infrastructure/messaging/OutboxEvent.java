package com.vextis.workflow.infrastructure.messaging;

import java.time.Instant;

record OutboxEvent(
        String eventId,
        String eventType,
        int eventVersion,
        String correlationId,
        String payload,
        Instant occurredAt
) {
}
