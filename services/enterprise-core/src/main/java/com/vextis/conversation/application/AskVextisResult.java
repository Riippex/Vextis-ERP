package com.vextis.conversation.application;

import com.vextis.conversation.domain.AgentActivityEvidence;
import com.vextis.conversation.domain.MemoryEvidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AskVextisResult(
        UUID conversationId,
        UUID messageId,
        String reply,
        Instant createdAt,
        List<AgentActivityEvidence> agentActivities,
        MemoryEvidence memoryEvidence
) {
    public AskVextisResult {
        agentActivities = List.copyOf(agentActivities);
    }
}
