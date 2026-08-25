package com.vextis.conversation.application;

import com.vextis.conversation.application.port.AgentChatClient;
import com.vextis.conversation.application.port.ConversationRepository;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TENANT_ID = "demo-tenant";

    @Test
    void startsANewConversationWhenNoneIsGiven() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeAgentChatClient agentChat = new FakeAgentChatClient("Here is the order status.");
        ConversationService service = new ConversationService(repository, agentChat, Clock.fixed(NOW, ZoneOffset.UTC));

        AskVextisResult result = service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", null, "What is the status of PO-2026-001?"));

        assertThat(result.reply()).isEqualTo("Here is the order status.");
        assertThat(agentChat.lastConversationId()).isEqualTo(result.conversationId());

        Conversation conversation = repository.findById(TENANT_ID, result.conversationId()).orElseThrow();
        assertThat(conversation.messages()).hasSize(2);
        assertThat(conversation.messages().get(0).sender()).isEqualTo(MessageSender.USER);
        assertThat(conversation.messages().get(1).sender()).isEqualTo(MessageSender.ASSISTANT);
        assertThat(conversation.messages().get(1).content()).isEqualTo("Here is the order status.");
    }

    @Test
    void appendsToAnExistingConversationForTheSameTenant() {
        InMemoryRepository repository = new InMemoryRepository();
        UUID conversationId = repository.startConversation(TENANT_ID, NOW);
        ConversationService service = new ConversationService(
                repository, new FakeAgentChatClient("Second reply."), Clock.fixed(NOW, ZoneOffset.UTC));

        AskVextisResult result = service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", conversationId, "Follow up question"));

        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(repository.findById(TENANT_ID, conversationId).orElseThrow().messages()).hasSize(2);
    }

    @Test
    void rejectsAConversationThatDoesNotBelongToTheTenant() {
        InMemoryRepository repository = new InMemoryRepository();
        UUID conversationId = repository.startConversation("another-tenant", NOW);
        ConversationService service = new ConversationService(
                repository, new FakeAgentChatClient("unused"), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", conversationId, "Hello")))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    private static final class InMemoryRepository implements ConversationRepository {
        private final Map<UUID, String> tenantByConversation = new HashMap<>();
        private final Map<UUID, List<ChatMessage>> messagesByConversation = new HashMap<>();

        @Override
        public UUID startConversation(String tenantId, Instant startedAt) {
            UUID id = UUID.randomUUID();
            tenantByConversation.put(id, tenantId);
            messagesByConversation.put(id, new ArrayList<>());
            return id;
        }

        @Override
        public boolean existsForTenant(String tenantId, UUID conversationId) {
            return tenantId.equals(tenantByConversation.get(conversationId));
        }

        @Override
        public ChatMessage appendMessage(
                String tenantId, UUID conversationId, MessageSender sender, String content,
                MessageKind kind, Instant occurredAt
        ) {
            ChatMessage message = new ChatMessage(UUID.randomUUID(), sender, content, kind, occurredAt);
            messagesByConversation.get(conversationId).add(message);
            return message;
        }

        @Override
        public Optional<Conversation> findById(String tenantId, UUID conversationId) {
            if (!tenantId.equals(tenantByConversation.get(conversationId))) {
                return Optional.empty();
            }
            return Optional.of(new Conversation(conversationId, tenantId, messagesByConversation.get(conversationId)));
        }
    }

    private static final class FakeAgentChatClient implements AgentChatClient {
        private final String reply;
        private UUID lastConversationId;

        private FakeAgentChatClient(String reply) {
            this.reply = reply;
        }

        @Override
        public String complete(String tenantId, UUID conversationId, String message) {
            this.lastConversationId = conversationId;
            return reply;
        }

        UUID lastConversationId() {
            return lastConversationId;
        }
    }
}
