package com.vextis.livesession.application.port;

import com.vextis.livesession.application.LiveSessionValidation;
import com.vextis.livesession.domain.LiveSession;

import java.time.Instant;
import java.util.UUID;

public interface LiveSessionRepository {

    void create(LiveSession session, String tokenHash);

    /**
     * Atomically checks the presented token's hash, the session's tenant-free
     * existence, its CREATED state, and its expiry, then transitions it to
     * ACTIVE — all in one conditional update, so this can only ever succeed
     * once for a given session.
     */
    LiveSessionValidation claim(UUID sessionId, String presentedTokenHash, Instant now);

    /** Closes a CREATED or ACTIVE session owned by this tenant. Returns false if nothing matched. */
    boolean close(String tenantId, UUID sessionId, Instant closedAt);
}
