package com.vextis.inventory.infrastructure.persistence;

import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.StockReservation;
import com.vextis.inventory.ReservationDirectory;
import com.vextis.inventory.application.port.StockRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcStockRepository implements StockRepository, ReservationDirectory {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcStockRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public StockDirectory.StockSummary setAvailability(String tenantId, String sku, int availableQuantity) {
        jdbc.update(
                """
                INSERT INTO inventory_stock (tenant_id, sku, available_quantity)
                VALUES (:tenantId, :sku, :availableQuantity)
                ON CONFLICT (tenant_id, sku)
                DO UPDATE SET available_quantity = EXCLUDED.available_quantity
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sku", sku)
                        .addValue("availableQuantity", availableQuantity)
        );
        return new StockDirectory.StockSummary(sku, availableQuantity);
    }

    @Override
    public void acquireReservationLocks(String tenantId, UUID orderId, String sku, String idempotencyKey) {
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                Map.of("lockKey", tenantId + ":inventory-reservation:idempotency:" + idempotencyKey),
                Long.class);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                Map.of("lockKey", tenantId + ":inventory-reservation:line:" + orderId + ':' + sku),
                Long.class);
    }

    @Override
    public Optional<StockReservation.Reservation> findReservationByIdempotencyKey(
            String tenantId, String idempotencyKey
    ) {
        return findReservation(
                "tenant_id = :tenantId AND idempotency_key = :idempotencyKey",
                Map.of("tenantId", tenantId, "idempotencyKey", idempotencyKey));
    }

    @Override
    public Optional<StockReservation.Reservation> findReservationByOrderLine(
            String tenantId, UUID orderId, String sku
    ) {
        return findReservation(
                "tenant_id = :tenantId AND order_id = :orderId AND sku = :sku",
                Map.of("tenantId", tenantId, "orderId", orderId, "sku", sku));
    }

    @Override
    public boolean decrementAvailableStock(String tenantId, String sku, int quantity) {
        return jdbc.update(
                """
                UPDATE inventory_stock
                SET available_quantity = available_quantity - :quantity,
                    reserved_quantity = reserved_quantity + :quantity
                WHERE tenant_id = :tenantId AND sku = :sku AND available_quantity >= :quantity
                """,
                Map.of("tenantId", tenantId, "sku", sku, "quantity", quantity)) == 1;
    }

    @Override
    public void saveReservation(StockReservation.Reservation reservation, StockReservation.Command command) {
        jdbc.update(
                """
                INSERT INTO inventory_reservations
                    (id, tenant_id, order_id, sku, quantity, status, actor_id,
                     correlation_id, idempotency_key, created_at)
                VALUES (:id, :tenantId, :orderId, :sku, :quantity, :status, :actorId,
                        :correlationId, :idempotencyKey, :createdAt)
                """,
                new MapSqlParameterSource().addValue("id", reservation.id())
                        .addValue("tenantId", command.tenantId()).addValue("orderId", reservation.orderId())
                        .addValue("sku", reservation.sku()).addValue("quantity", reservation.quantity())
                        .addValue("status", reservation.status().name()).addValue("actorId", command.actorId())
                        .addValue("correlationId", command.correlationId())
                        .addValue("idempotencyKey", command.idempotencyKey())
                        .addValue("createdAt", reservation.createdAt(), Types.TIMESTAMP_WITH_TIMEZONE));
        UUID auditId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action,
                     resource_type, resource_id, result, occurred_at)
                VALUES (:id, :tenantId, :correlationId, 'AGENT', :actorId,
                        'inventory.stock.reserved', 'INVENTORY_RESERVATION', :resourceId,
                        'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource().addValue("id", auditId).addValue("tenantId", command.tenantId())
                        .addValue("correlationId", command.correlationId()).addValue("actorId", command.actorId())
                        .addValue("resourceId", reservation.id())
                        .addValue("occurredAt", reservation.createdAt(), Types.TIMESTAMP_WITH_TIMEZONE));
        Map<String, Object> payload = Map.of(
                "reservation_id", reservation.id().toString(), "order_id", reservation.orderId().toString(),
                "sku", reservation.sku(), "quantity", reservation.quantity(), "status", reservation.status().name());
        UUID eventId = UUID.randomUUID();
        String envelope = toJson(Map.of(
                "event_id", eventId.toString(), "event_type", "inventory.reservation.created",
                "event_version", 1, "occurred_at", reservation.createdAt().toString(),
                "producer", "enterprise-core", "tenant_id", command.tenantId(),
                "correlation_id", command.correlationId(), "causation_id", auditId.toString(),
                "actor", Map.of("type", "AGENT", "id", command.actorId()), "payload", payload));
        jdbc.update(
                """
                INSERT INTO outbox_events
                    (event_id, event_type, event_version, aggregate_type, aggregate_id, tenant_id,
                     correlation_id, causation_id, payload, occurred_at)
                VALUES (:eventId, 'inventory.reservation.created', 1, 'INVENTORY_RESERVATION',
                        :aggregateId, :tenantId, :correlationId, :causationId, CAST(:payload AS JSONB), :occurredAt)
                """,
                new MapSqlParameterSource().addValue("eventId", eventId)
                        .addValue("aggregateId", reservation.id().toString()).addValue("tenantId", command.tenantId())
                        .addValue("correlationId", command.correlationId()).addValue("causationId", auditId)
                        .addValue("payload", envelope)
                        .addValue("occurredAt", reservation.createdAt(), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public List<StockReservation.Reservation> findReservations(String tenantId) {
        return jdbc.query(
                """
                SELECT id, order_id, sku, quantity, status, created_at
                FROM inventory_reservations WHERE tenant_id = :tenantId
                ORDER BY created_at DESC LIMIT 100
                """,
                Map.of("tenantId", tenantId), (rs, row) -> reservation(
                        rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getString("sku"), rs.getInt("quantity"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<StockReservation.Reservation> findAll(String tenantId) {
        return findReservations(tenantId);
    }

    @Override
    public List<StockReservation.Reservation> findByOrder(String tenantId, UUID orderId) {
        return jdbc.query(
                """
                SELECT id, order_id, sku, quantity, status, created_at
                FROM inventory_reservations
                WHERE tenant_id = :tenantId AND order_id = :orderId
                ORDER BY created_at
                """,
                Map.of("tenantId", tenantId, "orderId", orderId), (rs, row) -> reservation(
                        rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getString("sku"), rs.getInt("quantity"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    private Optional<StockReservation.Reservation> findReservation(String predicate, Map<String, ?> parameters) {
        return jdbc.query(
                "SELECT id, order_id, sku, quantity, status, created_at FROM inventory_reservations WHERE " + predicate,
                parameters, (rs, row) -> reservation(
                        rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getString("sku"), rs.getInt("quantity"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant())).stream().findFirst();
    }

    private StockReservation.Reservation reservation(
            UUID id, UUID orderId, String sku, int quantity, String status, Instant createdAt
    ) {
        return new StockReservation.Reservation(
                id, orderId, sku, quantity, StockReservation.Status.valueOf(status), createdAt);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize inventory reservation event", exception);
        }
    }
}
