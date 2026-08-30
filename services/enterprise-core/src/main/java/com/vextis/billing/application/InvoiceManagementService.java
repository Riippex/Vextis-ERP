package com.vextis.billing.application;

import com.vextis.billing.Invoice;
import com.vextis.billing.InvoiceDirectory;
import com.vextis.billing.InvoiceIssuer;
import com.vextis.billing.application.port.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
class InvoiceManagementService implements InvoiceIssuer, InvoiceDirectory {
    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal MAX_DATABASE_AMOUNT = new BigDecimal("99999999999999999.99");

    private final InvoiceRepository invoices;
    private final Clock clock;

    InvoiceManagementService(InvoiceRepository invoices, Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Invoice issue(IssueCommand command) {
        String tenantId = requireText(command.tenantId(), "Tenant id", 100);
        String actorId = requireText(command.actorId(), "Actor id", 150);
        String customerName = requireText(command.customerName(), "Customer name", 200);
        String correlationId = requireText(command.correlationId(), "Correlation id", 100);
        String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency key", 200);
        if (idempotencyKey.length() < 16 || command.orderId() == null || command.executionId() == null) {
            throw new IllegalArgumentException("Order, execution and an idempotency key of at least 16 characters are required");
        }
        String currency = requireText(command.currency(), "Currency", 3).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be an ISO 4217 code");
        }
        if (command.paymentTermsDays() < 0 || command.paymentTermsDays() > 365) {
            throw new IllegalArgumentException("Payment terms must be between 0 and 365 days");
        }
        if (command.lines() == null || command.lines().isEmpty() || command.lines().size() > 20) {
            throw new IllegalArgumentException("Invoice requires between 1 and 20 priced order lines");
        }

        invoices.acquireLocks(tenantId, command.orderId(), idempotencyKey);
        Optional<Invoice> replay = invoices.findByIdempotencyKey(tenantId, idempotencyKey);
        if (replay.isPresent()) {
            assertSameOrder(replay.get(), command.orderId());
            return replay.get();
        }
        Optional<Invoice> existing = invoices.findByOrder(tenantId, command.orderId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Order was already invoiced with a different idempotency key");
        }

        List<Invoice.Line> lines = command.lines().stream().map(this::priceLine).toList();
        BigDecimal subtotal = requireDatabaseAmount("Invoice subtotal", lines.stream()
                .map(Invoice.Line::lineSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        BigDecimal tax = requireDatabaseAmount(
                "Invoice tax", subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP));
        BigDecimal total = requireDatabaseAmount("Invoice total", subtotal.add(tax));
        Invoice invoice = new Invoice(
                UUID.randomUUID(), command.orderId(), command.executionId(), customerName, currency,
                subtotal, tax, total, Invoice.Status.ISSUED, command.paymentTermsDays(),
                clock.instant(), correlationId, lines);
        invoices.save(invoice, new IssueCommand(
                tenantId, actorId, command.orderId(), command.executionId(), customerName, currency,
                List.copyOf(command.lines()), command.paymentTermsDays(), correlationId, idempotencyKey));
        return invoice;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(String tenantId, UUID invoiceId) {
        return invoices.findById(requireText(tenantId, "Tenant id", 100), invoiceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findByExecution(String tenantId, UUID executionId) {
        return invoices.findByExecution(requireText(tenantId, "Tenant id", 100), executionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findRecent(String tenantId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invoice limit must be between 1 and 100");
        }
        return invoices.findRecent(requireText(tenantId, "Tenant id", 100), limit);
    }

    private Invoice.Line priceLine(LineInput input) {
        if (input == null || input.sku() == null || input.sku().isBlank()
                || !input.sku().trim().toUpperCase(Locale.ROOT).matches("[A-Z0-9._-]+")
                || input.quantity() < 1 || input.quantity() > 1_000_000 || input.unitPrice() == null) {
            throw new IllegalArgumentException("Every invoice line requires a valid SKU, quantity and explicit unit price");
        }
        BigDecimal unitPrice = input.unitPrice().setScale(2, RoundingMode.UNNECESSARY);
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Unit price must be greater than zero");
        }
        BigDecimal lineSubtotal = requireDatabaseAmount(
                "Invoice line subtotal",
                unitPrice.multiply(BigDecimal.valueOf(input.quantity())).setScale(2));
        return new Invoice.Line(input.sku().trim().toUpperCase(Locale.ROOT), input.quantity(), unitPrice,
                lineSubtotal);
    }

    private BigDecimal requireDatabaseAmount(String field, BigDecimal amount) {
        if (amount.abs().compareTo(MAX_DATABASE_AMOUNT) > 0) {
            throw new IllegalArgumentException(field + " exceeds the supported monetary range");
        }
        return amount;
    }

    private void assertSameOrder(Invoice invoice, UUID orderId) {
        if (!invoice.orderId().equals(orderId)) {
            throw new IllegalArgumentException("Invoice idempotency key was already used for another order");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }
}
