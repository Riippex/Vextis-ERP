package com.vextis.workflow.infrastructure.messaging;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
class OutboxEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    OutboxEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<OutboxEvent> lockNextBatch(int batchSize) {
        return jdbc.query(
                """
                SELECT event_id, event_type, event_version, correlation_id, payload::text, occurred_at
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY occurred_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """,
                Map.of("batchSize", batchSize),
                (rs, rowNumber) -> new OutboxEvent(
                        rs.getString("event_id"),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getString("correlation_id"),
                        rs.getString("payload"),
                        rs.getObject("occurred_at", Instant.class)
                )
        );
    }

    void markPublished(String eventId, Instant publishedAt) {
        int changed = jdbc.update(
                """
                UPDATE outbox_events
                SET published_at = :publishedAt
                WHERE event_id = :eventId AND published_at IS NULL
                """,
                Map.of("publishedAt", publishedAt, "eventId", eventId)
        );
        if (changed != 1) {
            throw new IllegalStateException("Outbox event was not available to mark as published");
        }
    }
}
