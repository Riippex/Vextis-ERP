package com.vextis.conversation.application;

import com.vextis.conversation.application.port.AgentChatClient;
import com.vextis.conversation.application.port.ConversationRepository;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationService implements AskVextisUseCase, FindConversationUseCase {

    private final ConversationRepository repository;
    private final AgentChatClient agentChat;
    private final Clock clock;

    public ConversationService(ConversationRepository repository, AgentChatClient agentChat, Clock clock) {
        this.repository = repository;
        this.agentChat = agentChat;
        this.clock = clock;
    }

    @Override
    public AskVextisResult postMessage(AskVextisCommand command) {
        UUID conversationId = command.conversationId();
        if (conversationId == null) {
            conversationId = repository.startConversation(command.tenantId(), clock.instant());
        } else if (!repository.existsForTenant(command.tenantId(), conversationId)) {
            throw new ConversationNotFoundException("Conversation was not found for this tenant");
        }

        repository.appendMessage(
                command.tenantId(), conversationId, MessageSender.USER, command.message(),
                MessageKind.TEXT, clock.instant());

        String reply = agentChat.complete(command.tenantId(), conversationId, command.message());

        ChatMessage assistantMessage = repository.appendMessage(
                command.tenantId(), conversationId, MessageSender.ASSISTANT, reply,
                MessageKind.TEXT, clock.instant());

        return new AskVextisResult(conversationId, assistantMessage.id(), reply, assistantMessage.occurredAt());
    }

    @Override
    public Optional<Conversation> findById(String tenantId, UUID conversationId) {
        return repository.findById(tenantId, conversationId);
    }
}
