package com.vextis.livesession.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity and state of a short, auditable Live-voice authorization. The
 * session token itself is never carried here — it is only ever handed to the
 * caller once, at creation, and persisted as a hash (see the repository port).
 */
public record LiveSession(
        UUID id,
        String tenantId,
        UUID conversationId,
        String actorId,
        LiveSessionState state,
        Instant createdAt,
        Instant expiresAt
) {
    public LiveSession {
        if (id == null || conversationId == null || state == null || createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Live session identity, state and timestamps are required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Actor id is required");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
