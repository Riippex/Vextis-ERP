package com.vextis.livesession.application;

import java.time.Instant;
import java.util.UUID;

public record LiveSessionValidation(boolean valid, String tenantId, UUID conversationId, Instant expiresAt) {

    public static LiveSessionValidation invalid() {
        return new LiveSessionValidation(false, null, null, null);
    }
}
