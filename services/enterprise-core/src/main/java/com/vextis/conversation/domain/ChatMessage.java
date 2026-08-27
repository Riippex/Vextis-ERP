package com.vextis.conversation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        MessageSender sender,
        String content,
        MessageKind kind,
        Instant occurredAt,
        List<AgentActivityEvidence> agentActivities,
        MemoryEvidence memoryEvidence
) {
    public ChatMessage {
        agentActivities = List.copyOf(agentActivities);
    }
}
