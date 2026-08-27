package com.vextis.conversation.application.port;

import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.AgentActivityEvidence;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
import com.vextis.conversation.domain.MemoryEvidence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {

    UUID startConversation(String tenantId, Instant startedAt);

    boolean existsForTenant(String tenantId, UUID conversationId);

    ChatMessage appendMessage(
            String tenantId,
            UUID conversationId,
            MessageSender sender,
            String content,
            MessageKind kind,
            Instant occurredAt,
            List<AgentActivityEvidence> agentActivities,
            MemoryEvidence memoryEvidence
    );

    Optional<Conversation> findById(String tenantId, UUID conversationId);
}
