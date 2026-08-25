package com.vextis.conversation.application;

import java.util.UUID;

public record AskVextisCommand(String tenantId, String actorId, UUID conversationId, String message) {
    public AskVextisCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Actor id is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }
        if (message.length() > 4000) {
            throw new IllegalArgumentException("Message must not exceed 4000 characters");
        }
    }
}
