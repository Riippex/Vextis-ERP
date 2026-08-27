package com.vextis.billing.infrastructure.persistence;

import com.vextis.billing.Invoice;
import com.vextis.billing.InvoiceIssuer;
import com.vextis.billing.application.port.InvoiceRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcInvoiceRepository implements InvoiceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcInvoiceRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void acquireLocks(String tenantId, UUID orderId, String idempotencyKey) {
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                Map.of("lockKey", tenantId + ":invoice:idempotency:" + idempotencyKey), Long.class);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                Map.of("lockKey", tenantId + ":invoice:order:" + orderId), Long.class);
    }

    @Override
    public Optional<Invoice> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return findOne("tenant_id = :tenantId AND idempotency_key = :idempotencyKey",
                Map.of("tenantId", tenantId, "idempotencyKey", idempotencyKey));
    }

    @Override
    public Optional<Invoice> findByOrder(String tenantId, UUID orderId) {
        return findOne("tenant_id = :tenantId AND order_id = :orderId",
                Map.of("tenantId", tenantId, "orderId", orderId));
    }

    @Override
    public Optional<Invoice> findById(String tenantId, UUID invoiceId) {
        return findOne("tenant_id = :tenantId AND id = :invoiceId",
                Map.of("tenantId", tenantId, "invoiceId", invoiceId));
    }

    @Override
    public Optional<Invoice> findByExecution(String tenantId, UUID executionId) {
        return findOne("tenant_id = :tenantId AND execution_id = :executionId",
                Map.of("tenantId", tenantId, "executionId", executionId));
    }

    @Override
    public List<Invoice> findRecent(String tenantId, int limit) {
        return jdbc.query(
                invoiceSelect() + " WHERE tenant_id = :tenantId ORDER BY issued_at DESC LIMIT :limit",
                Map.of("tenantId", tenantId, "limit", limit), this::mapInvoice);
    }

    @Override
    public void save(Invoice invoice, InvoiceIssuer.IssueCommand command) {
        jdbc.update(
                """
                INSERT INTO billing_invoices
                    (id, tenant_id, order_id, execution_id, customer_name, currency, subtotal, tax, total,
                     status, payment_terms_days, actor_id, correlation_id, idempotency_key, issued_at)
                VALUES
                    (:id, :tenantId, :orderId, :executionId, :customerName, :currency, :subtotal, :tax, :total,
                     :status, :paymentTermsDays, :actorId, :correlationId, :idempotencyKey, :issuedAt)
                """,
                new MapSqlParameterSource().addValue("id", invoice.id()).addValue("tenantId", command.tenantId())
                        .addValue("orderId", invoice.orderId()).addValue("executionId", invoice.executionId())
                        .addValue("customerName", invoice.customerName()).addValue("currency", invoice.currency())
                        .addValue("subtotal", invoice.subtotal()).addValue("tax", invoice.tax())
                        .addValue("total", invoice.total()).addValue("status", invoice.status().name())
                        .addValue("paymentTermsDays", invoice.paymentTermsDays()).addValue("actorId", command.actorId())
                        .addValue("correlationId", invoice.correlationId())
                        .addValue("idempotencyKey", command.idempotencyKey())
                        .addValue("issuedAt", invoice.issuedAt(), Types.TIMESTAMP_WITH_TIMEZONE));
        for (int index = 0; index < invoice.lines().size(); index++) {
            Invoice.Line line = invoice.lines().get(index);
            jdbc.update(
                    """
                    INSERT INTO billing_invoice_lines
                        (id, invoice_id, sequence_number, sku, quantity, unit_price, line_subtotal)
                    VALUES (:id, :invoiceId, :sequence, :sku, :quantity, :unitPrice, :lineSubtotal)
                    """,
                    new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                            .addValue("invoiceId", invoice.id()).addValue("sequence", index + 1)
                            .addValue("sku", line.sku()).addValue("quantity", line.quantity())
                            .addValue("unitPrice", line.unitPrice()).addValue("lineSubtotal", line.lineSubtotal()));
        }

        UUID auditId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO audit_records
                    (id, tenant_id, correlation_id, actor_type, actor_id, action,
                     resource_type, resource_id, result, occurred_at)
                VALUES (:id, :tenantId, :correlationId, 'AGENT', :actorId, 'billing.invoice.issued',
                        'BILLING_INVOICE', :resourceId, 'SUCCEEDED', :occurredAt)
                """,
                new MapSqlParameterSource().addValue("id", auditId).addValue("tenantId", command.tenantId())
                        .addValue("correlationId", invoice.correlationId()).addValue("actorId", command.actorId())
                        .addValue("resourceId", invoice.id())
                        .addValue("occurredAt", invoice.issuedAt(), Types.TIMESTAMP_WITH_TIMEZONE));

        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "invoice_id", invoice.id().toString(), "order_id", invoice.orderId().toString(),
                "execution_id", invoice.executionId().toString(), "currency", invoice.currency(),
                "subtotal", invoice.subtotal().toPlainString(), "tax", invoice.tax().toPlainString(),
                "total", invoice.total().toPlainString(), "status", invoice.status().name());
        String envelope = toJson(Map.of(
                "event_id", eventId.toString(), "event_type", "billing.invoice.issued", "event_version", 1,
                "occurred_at", invoice.issuedAt().toString(), "producer", "enterprise-core",
                "tenant_id", command.tenantId(), "correlation_id", invoice.correlationId(),
                "causation_id", auditId.toString(), "actor", Map.of("type", "AGENT", "id", command.actorId()),
                "payload", payload));
        jdbc.update(
                """
                INSERT INTO outbox_events
                    (event_id, event_type, event_version, aggregate_type, aggregate_id, tenant_id,
                     correlation_id, causation_id, payload, occurred_at)
                VALUES (:eventId, 'billing.invoice.issued', 1, 'BILLING_INVOICE', :aggregateId, :tenantId,
                        :correlationId, :causationId, CAST(:payload AS JSONB), :occurredAt)
                """,
                new MapSqlParameterSource().addValue("eventId", eventId).addValue("aggregateId", invoice.id().toString())
                        .addValue("tenantId", command.tenantId()).addValue("correlationId", invoice.correlationId())
                        .addValue("causationId", auditId).addValue("payload", envelope)
                        .addValue("occurredAt", invoice.issuedAt(), Types.TIMESTAMP_WITH_TIMEZONE));
        jdbc.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, operation, idempotency_key, response_code, response_body)
                VALUES (:id, :tenantId, 'billing.issue-invoice', :idempotencyKey, 201, CAST(:body AS JSONB))
                """,
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("tenantId", command.tenantId())
                        .addValue("idempotencyKey", command.idempotencyKey())
                        .addValue("body", toJson(Map.of("invoiceId", invoice.id().toString()))));
    }

    private Optional<Invoice> findOne(String predicate, Map<String, ?> parameters) {
        return jdbc.query(invoiceSelect() + " WHERE " + predicate, parameters, this::mapInvoice).stream().findFirst();
    }

    private String invoiceSelect() {
        return """
                SELECT id, order_id, execution_id, customer_name, currency, subtotal, tax, total,
                       status, payment_terms_days, issued_at, correlation_id
                FROM billing_invoices
                """;
    }

    private Invoice mapInvoice(ResultSet rs, int rowNumber) throws SQLException {
        UUID invoiceId = rs.getObject("id", UUID.class);
        return new Invoice(
                invoiceId, rs.getObject("order_id", UUID.class), rs.getObject("execution_id", UUID.class),
                rs.getString("customer_name"), rs.getString("currency"), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax"), rs.getBigDecimal("total"), Invoice.Status.valueOf(rs.getString("status")),
                rs.getInt("payment_terms_days"), rs.getObject("issued_at", OffsetDateTime.class).toInstant(),
                rs.getString("correlation_id"), findLines(invoiceId));
    }

    private List<Invoice.Line> findLines(UUID invoiceId) {
        return jdbc.query(
                """
                SELECT sku, quantity, unit_price, line_subtotal FROM billing_invoice_lines
                WHERE invoice_id = :invoiceId ORDER BY sequence_number
                """,
                Map.of("invoiceId", invoiceId), (rs, row) -> new Invoice.Line(
                        rs.getString("sku"), rs.getInt("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("line_subtotal")));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize invoice evidence", exception);
        }
    }
}
