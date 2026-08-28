package com.vextis.livesession.infrastructure.persistence;

import com.vextis.livesession.application.LiveSessionValidation;
import com.vextis.livesession.application.port.LiveSessionRepository;
import com.vextis.livesession.domain.LiveSession;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcLiveSessionRepository implements LiveSessionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcLiveSessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(LiveSession session, String tokenHash) {
        jdbc.update(
                """
                INSERT INTO live_sessions
                    (id, tenant_id, conversation_id, actor_id, state, token_hash, created_at, expires_at)
                VALUES
                    (:id, :tenantId, :conversationId, :actorId, :state, :tokenHash, :createdAt, :expiresAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", session.id())
                        .addValue("tenantId", session.tenantId())
                        .addValue("conversationId", session.conversationId())
                        .addValue("actorId", session.actorId())
                        .addValue("state", session.state().name())
                        .addValue("tokenHash", tokenHash)
                        .addValue("createdAt", Timestamp.from(session.createdAt()))
                        .addValue("expiresAt", Timestamp.from(session.expiresAt())));
    }

    @Override
    public int countCreatedSince(String tenantId, String actorId, Instant since) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM live_sessions
                WHERE tenant_id = :tenantId
                  AND actor_id = :actorId
                  AND created_at >= :since
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("actorId", actorId)
                        .addValue("since", Timestamp.from(since)),
                Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public LiveSessionValidation claim(UUID sessionId, String presentedTokenHash, Instant now) {
        List<LiveSessionValidation> claimed = jdbc.query(
                """
                UPDATE live_sessions
                SET state = 'ACTIVE'
                WHERE id = :id
                  AND token_hash = :tokenHash
                  AND state = 'CREATED'
                  AND expires_at > :now
                RETURNING tenant_id, conversation_id, expires_at
                """,
                new MapSqlParameterSource()
                        .addValue("id", sessionId)
                        .addValue("tokenHash", presentedTokenHash)
                        .addValue("now", Timestamp.from(now)),
                (rs, row) -> new LiveSessionValidation(
                        true,
                        rs.getString("tenant_id"),
                        rs.getObject("conversation_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant()));
        return claimed.stream().findFirst().orElseGet(LiveSessionValidation::invalid);
    }

    @Override
    public boolean close(String tenantId, UUID sessionId, Instant closedAt) {
        int updated = jdbc.update(
                """
                UPDATE live_sessions
                SET state = 'CLOSED', closed_at = :closedAt
                WHERE id = :id AND tenant_id = :tenantId AND state IN ('CREATED', 'ACTIVE')
                """,
                Map.of("id", sessionId, "tenantId", tenantId, "closedAt", Timestamp.from(closedAt)));
        return updated == 1;
    }
}
