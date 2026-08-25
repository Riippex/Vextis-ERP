package com.vextis.conversation.application;

import com.vextis.conversation.domain.Conversation;

import java.util.Optional;
import java.util.UUID;

public interface FindConversationUseCase {
    Optional<Conversation> findById(String tenantId, UUID conversationId);
}
