package com.vextis.conversation.application;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.conversation.application.port.AgentChatClient;
import com.vextis.conversation.application.port.ConversationRepository;
import com.vextis.conversation.domain.AgentActivityEvidence;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
import com.vextis.conversation.domain.MemoryEvidence;
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
        ConversationService service = service(repository, agentChat, new FakeAgentDirectory(List.of()));

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
        ConversationService service = service(
                repository, new FakeAgentChatClient("Second reply."), new FakeAgentDirectory(List.of()));

        AskVextisResult result = service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", conversationId, "Follow up question"));

        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(repository.findById(TENANT_ID, conversationId).orElseThrow().messages()).hasSize(2);
    }

    @Test
    void sendsExistingTenantScopedTurnsAsConversationContext() {
        InMemoryRepository repository = new InMemoryRepository();
        UUID conversationId = repository.startConversation(TENANT_ID, NOW);
        repository.appendMessage(
                TENANT_ID, conversationId, MessageSender.USER, "de Acme Colombia",
                MessageKind.TEXT, NOW, List.of(), null);
        repository.appendMessage(
                TENANT_ID, conversationId, MessageSender.ASSISTANT,
                "Encontré el registro activo de Acme Colombia.",
                MessageKind.TEXT, NOW, List.of(), null);
        FakeAgentChatClient agentChat = new FakeAgentChatClient("Estas son sus cotizaciones.");
        ConversationService service = service(repository, agentChat, new FakeAgentDirectory(List.of()));

        service.postMessage(new AskVextisCommand(
                TENANT_ID, "firebase-user-123", conversationId, "¿Qué cotizaciones hay?"));

        assertThat(agentChat.lastHistory()).containsExactly(
                new AgentChatClient.ConversationTurn(
                        AgentChatClient.ConversationRole.USER, "de Acme Colombia"),
                new AgentChatClient.ConversationTurn(
                        AgentChatClient.ConversationRole.ASSISTANT,
                        "Encontré el registro activo de Acme Colombia."));
    }

    @Test
    void boundsConversationContextByTurnAndCharacterBudgets() {
        InMemoryRepository repository = new InMemoryRepository();
        UUID conversationId = repository.startConversation(TENANT_ID, NOW);
        for (int index = 0; index < 13; index++) {
            repository.appendMessage(
                    TENANT_ID, conversationId, MessageSender.USER, "turn-" + index,
                    MessageKind.TEXT, NOW, List.of(), null);
        }
        FakeAgentChatClient turnBoundedChat = new FakeAgentChatClient("bounded");
        service(repository, turnBoundedChat, new FakeAgentDirectory(List.of())).postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", conversationId, "follow up"));

        assertThat(turnBoundedChat.lastHistory()).hasSize(12);
        assertThat(turnBoundedChat.lastHistory().getFirst().content()).isEqualTo("turn-1");

        InMemoryRepository characterRepository = new InMemoryRepository();
        UUID characterConversationId = characterRepository.startConversation(TENANT_ID, NOW);
        for (int index = 0; index < 12; index++) {
            characterRepository.appendMessage(
                    TENANT_ID, characterConversationId, MessageSender.USER, "x".repeat(1_000),
                    MessageKind.TEXT, NOW, List.of(), null);
        }
        characterRepository.appendMessage(
                TENANT_ID, characterConversationId, MessageSender.ASSISTANT, "y".repeat(5_000),
                MessageKind.TEXT, NOW, List.of(), null);
        FakeAgentChatClient characterBoundedChat = new FakeAgentChatClient("bounded");
        service(characterRepository, characterBoundedChat, new FakeAgentDirectory(List.of())).postMessage(
                new AskVextisCommand(
                        TENANT_ID, "firebase-user-123", characterConversationId, "follow up"));

        assertThat(characterBoundedChat.lastHistory()).hasSize(9);
        assertThat(characterBoundedChat.lastHistory().getLast().content()).hasSize(4_000);
        assertThat(characterBoundedChat.lastHistory().stream()
                .mapToInt(turn -> turn.content().length())
                .sum()).isEqualTo(12_000);
    }

    @Test
    void rejectsAConversationThatDoesNotBelongToTheTenant() {
        InMemoryRepository repository = new InMemoryRepository();
        UUID conversationId = repository.startConversation("another-tenant", NOW);
        ConversationService service = service(
                repository, new FakeAgentChatClient("unused"), new FakeAgentDirectory(List.of()));

        assertThatThrownBy(() -> service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", conversationId, "Hello")))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void snapshotsOnlyTrustedRegisteredAgentActivityAndAllowedTools() {
        InMemoryRepository repository = new InMemoryRepository();
        AgentDirectory.AgentRegistration inventory = registration(
                "vextis_inventory_agent", "coordinator-agent", List.of("get_stock"));
        AgentDirectory.AgentRegistration untrusted = registration(
                "untrusted_agent", "another-service", List.of("get_stock"));
        FakeAgentChatClient agentChat = new FakeAgentChatClient(
                "There are 12 units available.",
                List.of(
                        new AgentChatClient.AgentActivity(
                                "vextis_inventory_agent", List.of("get_stock", "reserve_stock", "get_stock")),
                        new AgentChatClient.AgentActivity("untrusted_agent", List.of("get_stock")),
                        new AgentChatClient.AgentActivity("unknown_agent", List.of("get_stock"))));
        ConversationService service = service(
                repository, agentChat, new FakeAgentDirectory(List.of(inventory, untrusted)));

        AskVextisResult result = service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", null, "How much stock is available?"));

        assertThat(result.agentActivities()).containsExactly(new AgentActivityEvidence(
                "vextis_inventory_agent", "1.0.0", "Inventory Agent", "gemini-3.5-flash", "1.0.0",
                List.of("get_stock")));
        Conversation conversation = repository.findById(TENANT_ID, result.conversationId()).orElseThrow();
        assertThat(conversation.messages().get(1).agentActivities()).isEqualTo(result.agentActivities());
    }

    @Test
    void passesTheAuthenticatedActorAndSnapshotsOnlyValidMemoryEvidence() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeAgentChatClient agentChat = new FakeAgentChatClient(
                "I will answer in Spanish.", List.of(),
                new AgentChatClient.MemoryActivity("VERTEX_AI_MEMORY_BANK", true, 2, true));
        ConversationService service = service(repository, agentChat, new FakeAgentDirectory(List.of()));

        AskVextisResult result = service.postMessage(
                new AskVextisCommand(TENANT_ID, "firebase-user-123", null, "Remember preference: Spanish"));

        assertThat(agentChat.lastActorId()).isEqualTo("firebase-user-123");
        assertThat(result.memoryEvidence()).isEqualTo(
                new MemoryEvidence("VERTEX_AI_MEMORY_BANK", true, 2, true));
        assertThat(repository.findById(TENANT_ID, result.conversationId()).orElseThrow()
                .messages().get(1).memoryEvidence()).isEqualTo(result.memoryEvidence());
    }

    private static ConversationService service(
            ConversationRepository repository,
            AgentChatClient agentChat,
            AgentDirectory agents
    ) {
        return new ConversationService(
                repository, agentChat, agents, Clock.fixed(NOW, ZoneOffset.UTC), "coordinator-agent");
    }

    private static AgentDirectory.AgentRegistration registration(
            String agentId,
            String serviceIdentity,
            List<String> allowedTools
    ) {
        return new AgentDirectory.AgentRegistration(
                agentId, "1.0.0", "Inventory Agent", "INVENTORY_OPERATIONS", "Looks up stock.",
                "GOOGLE_ADK", "gemini-3.5-flash", "1.0.0", serviceIdentity, "ACTIVE",
                List.of("stock lookup"), allowedTools);
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
        public ChatMessage appendMessage(
                String tenantId, UUID conversationId, MessageSender sender, String content,
                MessageKind kind, Instant occurredAt, List<AgentActivityEvidence> agentActivities,
                MemoryEvidence memoryEvidence
        ) {
            ChatMessage message = new ChatMessage(
                    UUID.randomUUID(), sender, content, kind, occurredAt, agentActivities, memoryEvidence);
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
        private final List<AgentActivity> activities;
        private final MemoryActivity memory;
        private UUID lastConversationId;
        private String lastActorId;
        private List<ConversationTurn> lastHistory = List.of();

        private FakeAgentChatClient(String reply) {
            this(reply, List.of());
        }

        private FakeAgentChatClient(String reply, List<AgentActivity> activities) {
            this(reply, activities, null);
        }

        private FakeAgentChatClient(String reply, List<AgentActivity> activities, MemoryActivity memory) {
            this.reply = reply;
            this.activities = activities;
            this.memory = memory;
        }

        @Override
        public ChatCompletion complete(
                String tenantId,
                String actorId,
                UUID conversationId,
                List<ConversationTurn> history,
                String message
        ) {
            this.lastConversationId = conversationId;
            this.lastActorId = actorId;
            this.lastHistory = List.copyOf(history);
            return new ChatCompletion(reply, activities, memory);
        }

        UUID lastConversationId() {
            return lastConversationId;
        }

        String lastActorId() {
            return lastActorId;
        }

        List<ConversationTurn> lastHistory() {
            return lastHistory;
        }
    }

    private static final class FakeAgentDirectory implements AgentDirectory {
        private final List<AgentRegistration> registrations;

        private FakeAgentDirectory(List<AgentRegistration> registrations) {
            this.registrations = registrations;
        }

        @Override
        public List<AgentRegistration> findAll(String tenantId) {
            return registrations;
        }

        @Override
        public Optional<AgentRegistration> findActive(String tenantId, String agentId) {
            return registrations.stream()
                    .filter(registration -> registration.agentId().equals(agentId))
                    .filter(registration -> registration.status().equals("ACTIVE"))
                    .findFirst();
        }
    }
}
