package com.vextis.livesession.application;

import java.util.UUID;

public interface ValidateLiveSessionUseCase {
    /**
     * Claims the session for Agent Runtime's WebSocket handshake. Succeeds at
     * most once per session — a second call with the same or any token
     * returns invalid, so a captured token cannot be reused for a second
     * connection.
     */
    LiveSessionValidation validate(UUID sessionId, String presentedToken);
}
