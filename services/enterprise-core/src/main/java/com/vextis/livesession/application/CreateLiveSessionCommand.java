package com.vextis.livesession.application;

import java.util.UUID;

public record CreateLiveSessionCommand(String tenantId, String actorId, UUID conversationId) {
    public CreateLiveSessionCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Actor id is required");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("Conversation id is required");
        }
    }
}
