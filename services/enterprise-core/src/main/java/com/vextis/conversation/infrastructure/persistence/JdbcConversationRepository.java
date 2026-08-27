package com.vextis.conversation.infrastructure.persistence;

import com.vextis.conversation.ConversationActivityOverview;
import com.vextis.conversation.application.port.ConversationRepository;
import com.vextis.conversation.domain.AgentActivityEvidence;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
import com.vextis.conversation.domain.MemoryEvidence;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcConversationRepository implements ConversationRepository, ConversationActivityOverview {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcConversationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID startConversation(String tenantId, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO conversations (id, tenant_id, started_at) VALUES (:id, :tenantId, :startedAt)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("startedAt", Timestamp.from(startedAt)));
        return id;
    }

    @Override
    public boolean existsForTenant(String tenantId, UUID conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE id = :id AND tenant_id = :tenantId",
                Map.of("id", conversationId, "tenantId", tenantId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public ChatMessage appendMessage(
            String tenantId,
            UUID conversationId,
            MessageSender sender,
            String content,
            MessageKind kind,
            Instant occurredAt,
            List<AgentActivityEvidence> agentActivities,
            MemoryEvidence memoryEvidence
    ) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update(
                """
                INSERT INTO chat_messages (id, conversation_id, sender, kind, content, occurred_at)
                SELECT :id, :conversationId, :sender, :kind, :content, :occurredAt
                WHERE EXISTS (
                    SELECT 1 FROM conversations WHERE id = :conversationId AND tenant_id = :tenantId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("conversationId", conversationId)
                        .addValue("tenantId", tenantId)
                        .addValue("sender", sender.name())
                        .addValue("kind", kind.name())
                        .addValue("content", content)
                        .addValue("occurredAt", Timestamp.from(occurredAt)));
        if (inserted != 1) {
            throw new IllegalStateException("Conversation was not found for this tenant");
        }
        for (int sequence = 0; sequence < agentActivities.size(); sequence++) {
            AgentActivityEvidence activity = agentActivities.get(sequence);
            jdbc.update(
                    """
                    INSERT INTO chat_message_agent_activities (
                        message_id, sequence, agent_id, agent_version, display_name,
                        model_id, prompt_version, tool_names, occurred_at
                    ) VALUES (
                        :messageId, :sequence, :agentId, :agentVersion, :displayName,
                        :modelId, :promptVersion, string_to_array(:toolNames, ','), :occurredAt
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("messageId", id)
                            .addValue("sequence", sequence)
                            .addValue("agentId", activity.agentId())
                            .addValue("agentVersion", activity.agentVersion())
                            .addValue("displayName", activity.displayName())
                            .addValue("modelId", activity.modelId())
                            .addValue("promptVersion", activity.promptVersion())
                            .addValue("toolNames", String.join(",", activity.tools()))
                            .addValue("occurredAt", Timestamp.from(occurredAt)));
        }
        if (memoryEvidence != null) {
            jdbc.update(
                    """
                    INSERT INTO chat_message_memory_evidence (
                        message_id, provider, available, context_count, preference_stored
                    ) VALUES (
                        :messageId, :provider, :available, :contextCount, :preferenceStored
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("messageId", id)
                            .addValue("provider", memoryEvidence.provider())
                            .addValue("available", memoryEvidence.available())
                            .addValue("contextCount", memoryEvidence.contextCount())
                            .addValue("preferenceStored", memoryEvidence.preferenceStored()));
        }
        return new ChatMessage(id, sender, content, kind, occurredAt, agentActivities, memoryEvidence);
    }

    @Override
    public Optional<Conversation> findById(String tenantId, UUID conversationId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM conversations WHERE id = :id AND tenant_id = :tenantId",
                Map.of("id", conversationId, "tenantId", tenantId),
                (rs, row) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            return Optional.empty();
        }

        List<ChatMessage> messagesWithoutEvidence = jdbc.query(
                """
                SELECT id, sender, kind, content, occurred_at
                FROM chat_messages
                WHERE conversation_id = :conversationId
                ORDER BY occurred_at ASC
                """,
                Map.of("conversationId", conversationId),
                (rs, row) -> new ChatMessage(
                        rs.getObject("id", UUID.class),
                        MessageSender.valueOf(rs.getString("sender")),
                        rs.getString("content"),
                        MessageKind.valueOf(rs.getString("kind")),
                        rs.getTimestamp("occurred_at").toInstant(),
                        List.of(), null));

        Map<UUID, List<AgentActivityEvidence>> evidenceByMessage = new HashMap<>();
        List<MessageEvidence> evidenceRows = jdbc.query(
                """
                SELECT activity.message_id, activity.agent_id, activity.agent_version,
                       activity.display_name, activity.model_id, activity.prompt_version,
                       activity.tool_names
                FROM chat_message_agent_activities activity
                JOIN chat_messages message ON message.id = activity.message_id
                JOIN conversations conversation ON conversation.id = message.conversation_id
                WHERE conversation.id = :conversationId
                  AND conversation.tenant_id = :tenantId
                ORDER BY activity.sequence ASC
                """,
                Map.of("conversationId", conversationId, "tenantId", tenantId),
                (rs, row) -> new MessageEvidence(
                        rs.getObject("message_id", UUID.class),
                        new AgentActivityEvidence(
                                rs.getString("agent_id"),
                                rs.getString("agent_version"),
                                rs.getString("display_name"),
                                rs.getString("model_id"),
                                rs.getString("prompt_version"),
                                toList(rs.getArray("tool_names")))));
        evidenceRows.forEach(row -> evidenceByMessage
                .computeIfAbsent(row.messageId(), ignored -> new java.util.ArrayList<>())
                .add(row.evidence()));

        Map<UUID, MemoryEvidence> memoryByMessage = new HashMap<>();
        jdbc.query(
                """
                SELECT memory.message_id, memory.provider, memory.available,
                       memory.context_count, memory.preference_stored
                FROM chat_message_memory_evidence memory
                JOIN chat_messages message ON message.id = memory.message_id
                JOIN conversations conversation ON conversation.id = message.conversation_id
                WHERE conversation.id = :conversationId
                  AND conversation.tenant_id = :tenantId
                """,
                Map.of("conversationId", conversationId, "tenantId", tenantId),
                rs -> {
                    memoryByMessage.put(
                            rs.getObject("message_id", UUID.class),
                            new MemoryEvidence(
                                    rs.getString("provider"),
                                    rs.getBoolean("available"),
                                    rs.getInt("context_count"),
                                    rs.getBoolean("preference_stored")));
                });

        List<ChatMessage> messages = messagesWithoutEvidence.stream()
                .map(message -> new ChatMessage(
                        message.id(), message.sender(), message.content(), message.kind(), message.occurredAt(),
                        evidenceByMessage.getOrDefault(message.id(), List.of()),
                        memoryByMessage.get(message.id())))
                .toList();

        return Optional.of(new Conversation(conversationId, tenantId, messages));
    }

    @Override
    public List<RecentAgentActivity> findRecentAgentActivities(String tenantId, int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return jdbc.query(
                """
                SELECT message.conversation_id, activity.message_id, activity.agent_id,
                       activity.agent_version, activity.display_name, activity.model_id,
                       activity.prompt_version, activity.tool_names, activity.occurred_at
                FROM chat_message_agent_activities activity
                JOIN chat_messages message ON message.id = activity.message_id
                JOIN conversations conversation ON conversation.id = message.conversation_id
                WHERE conversation.tenant_id = :tenantId
                ORDER BY activity.occurred_at DESC, activity.sequence ASC
                LIMIT :limit
                """,
                Map.of("tenantId", tenantId, "limit", limit),
                (rs, row) -> new RecentAgentActivity(
                        rs.getObject("conversation_id", UUID.class),
                        rs.getObject("message_id", UUID.class),
                        rs.getString("agent_id"),
                        rs.getString("agent_version"),
                        rs.getString("display_name"),
                        rs.getString("model_id"),
                        rs.getString("prompt_version"),
                        toList(rs.getArray("tool_names")),
                        rs.getTimestamp("occurred_at").toInstant())
        );
    }

    private static List<String> toList(Array sqlArray) throws SQLException {
        try {
            return List.copyOf(java.util.Arrays.asList((String[]) sqlArray.getArray()));
        } finally {
            sqlArray.free();
        }
    }

    private record MessageEvidence(UUID messageId, AgentActivityEvidence evidence) {
    }
}
