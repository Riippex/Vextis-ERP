package com.vextis.conversation.infrastructure.persistence;

import com.vextis.conversation.application.port.ConversationRepository;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcConversationRepository implements ConversationRepository {

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
    public ChatMessage appendMessage(
            String tenantId,
            UUID conversationId,
            MessageSender sender,
            String content,
            MessageKind kind,
            Instant occurredAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
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
        return new ChatMessage(id, sender, content, kind, occurredAt);
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

        List<ChatMessage> messages = jdbc.query(
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
                        rs.getTimestamp("occurred_at").toInstant()));

        return Optional.of(new Conversation(conversationId, tenantId, messages));
    }
}
