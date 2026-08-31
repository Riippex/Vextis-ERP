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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ConversationService implements AskVextisUseCase, FindConversationUseCase {

    private static final Pattern TOOL_NAME = Pattern.compile("[a-z0-9_]{1,100}");
    private static final int MAX_HISTORY_TURNS = 12;
    private static final int MAX_HISTORY_TURN_CHARACTERS = 4_000;
    private static final int MAX_HISTORY_CHARACTERS = 12_000;

    private final ConversationRepository repository;
    private final AgentChatClient agentChat;
    private final AgentDirectory agents;
    private final Clock clock;
    private final String trustedServiceIdentity;

    public ConversationService(
            ConversationRepository repository,
            AgentChatClient agentChat,
            AgentDirectory agents,
            Clock clock,
            @Value("${vextis.agent-tools.coordinator-agent-id:coordinator-agent}") String trustedServiceIdentity
    ) {
        this.repository = repository;
        this.agentChat = agentChat;
        this.agents = agents;
        this.clock = clock;
        this.trustedServiceIdentity = trustedServiceIdentity;
    }

    @Override
    public AskVextisResult postMessage(AskVextisCommand command) {
        UUID conversationId = command.conversationId();
        List<AgentChatClient.ConversationTurn> history = List.of();
        if (conversationId == null) {
            conversationId = repository.startConversation(command.tenantId(), clock.instant());
        } else {
            Conversation conversation = repository.findById(command.tenantId(), conversationId)
                    .orElseThrow(() -> new ConversationNotFoundException(
                            "Conversation was not found for this tenant"));
            history = boundedHistory(conversation.messages());
        }

        repository.appendMessage(
                command.tenantId(), conversationId, MessageSender.USER, command.message(),
                MessageKind.TEXT, clock.instant(), List.of(), null);

        AgentChatClient.ChatCompletion completion = agentChat.complete(
                command.tenantId(), command.actorId(), conversationId, history, command.message());
        List<AgentActivityEvidence> evidence = validateEvidence(command.tenantId(), completion.activities());
        MemoryEvidence memoryEvidence = validateMemoryEvidence(completion.memory());

        ChatMessage assistantMessage = repository.appendMessage(
                command.tenantId(), conversationId, MessageSender.ASSISTANT, completion.reply(),
                MessageKind.TEXT, clock.instant(), evidence, memoryEvidence);

        return new AskVextisResult(
                conversationId, assistantMessage.id(), completion.reply(), assistantMessage.occurredAt(), evidence,
                memoryEvidence);
    }

    @Override
    public Optional<Conversation> findById(String tenantId, UUID conversationId) {
        return repository.findById(tenantId, conversationId);
    }

    private List<AgentChatClient.ConversationTurn> boundedHistory(List<ChatMessage> messages) {
        Deque<AgentChatClient.ConversationTurn> selected = new ArrayDeque<>();
        int remainingCharacters = MAX_HISTORY_CHARACTERS;
        for (int index = messages.size() - 1;
             index >= 0 && selected.size() < MAX_HISTORY_TURNS && remainingCharacters > 0;
             index--) {
            ChatMessage message = messages.get(index);
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            int contentLength = Math.min(
                    message.content().length(),
                    Math.min(MAX_HISTORY_TURN_CHARACTERS, remainingCharacters));
            selected.addFirst(new AgentChatClient.ConversationTurn(
                    AgentChatClient.ConversationRole.valueOf(message.sender().name()),
                    message.content().substring(0, contentLength)));
            remainingCharacters -= contentLength;
        }
        return List.copyOf(selected);
    }

    private List<AgentActivityEvidence> validateEvidence(
            String tenantId,
            List<AgentChatClient.AgentActivity> claimedActivities
    ) {
        Map<String, AgentChatClient.AgentActivity> uniqueActivities = new LinkedHashMap<>();
        claimedActivities.stream().limit(4).forEach(activity -> {
            if (activity.agentId() != null) {
                uniqueActivities.putIfAbsent(activity.agentId(), activity);
            }
        });
        return uniqueActivities.values().stream()
                .map(activity -> agents.findActive(tenantId, activity.agentId())
                        .filter(agent -> trustedServiceIdentity.equals(agent.serviceIdentity()))
                        .map(agent -> new AgentActivityEvidence(
                                agent.agentId(),
                                agent.version(),
                                agent.displayName(),
                                agent.modelId(),
                                agent.promptVersion(),
                                activity.tools().stream()
                                        .filter(tool -> tool != null && TOOL_NAME.matcher(tool).matches())
                                        .filter(agent.allowedTools()::contains)
                                        .distinct()
                                        .limit(8)
                                        .toList()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private MemoryEvidence validateMemoryEvidence(AgentChatClient.MemoryActivity claimed) {
        if (claimed == null
                || !"VERTEX_AI_MEMORY_BANK".equals(claimed.provider())
                || claimed.contextCount() < 0
                || claimed.contextCount() > 5
                || (!claimed.available() && claimed.contextCount() != 0)) {
            return null;
        }
        return new MemoryEvidence(
                claimed.provider(), claimed.available(), claimed.contextCount(), claimed.preferenceStored());
    }
}
