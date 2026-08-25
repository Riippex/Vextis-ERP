package com.vextis.conversation.domain;

import java.util.List;
import java.util.UUID;

public record Conversation(UUID id, String tenantId, List<ChatMessage> messages) {
    public Conversation {
        messages = List.copyOf(messages);
    }
}
