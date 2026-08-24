package com.vextis.inventory.application;

import com.vextis.audit.AuditTrail;
import com.vextis.inventory.StockAdministration;
import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.StockReservation;
import com.vextis.inventory.application.port.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
class StockManagementService implements StockAdministration, StockReservation {

    private final StockRepository stock;
    private final AuditTrail audit;
    private final Clock clock;

    StockManagementService(StockRepository stock, AuditTrail audit, Clock clock) {
        this.stock = stock;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StockDirectory.StockSummary setAvailability(SetAvailabilityCommand command) {
        String tenantId = requireText(command.tenantId(), "Tenant id", 100);
        String actorId = requireText(command.actorId(), "Actor id", 150);
        String sku = requireText(command.sku(), "SKU", 100).toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9._-]+")) {
            throw new IllegalArgumentException("SKU may only contain letters, numbers, dots, underscores and hyphens");
        }
        if (command.availableQuantity() < 0 || command.availableQuantity() > 1_000_000) {
            throw new IllegalArgumentException("Available quantity must be between 0 and 1000000");
        }
        StockDirectory.StockSummary saved = stock.setAvailability(
                tenantId, sku, command.availableQuantity());
        UUID resourceId = UUID.nameUUIDFromBytes((tenantId + ':' + sku).getBytes(StandardCharsets.UTF_8));
        audit.recordUserAction(new AuditTrail.UserAction(
                tenantId,
                UUID.randomUUID().toString(),
                actorId,
                "inventory.stock.availability-set",
                "StockItem",
                resourceId,
                clock.instant()
        ));
        return saved;
    }

    @Override
    @Transactional
    public Reservation reserve(Command command) {
        String tenantId = requireText(command.tenantId(), "Tenant id", 100);
        String actorId = requireText(command.actorId(), "Actor id", 150);
        String correlationId = requireText(command.correlationId(), "Correlation id", 100);
        String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency key", 200);
        if (idempotencyKey.length() < 16 || command.orderId() == null) {
            throw new IllegalArgumentException("Order id and an idempotency key of at least 16 characters are required");
        }
        String sku = requireText(command.sku(), "SKU", 100).toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9._-]+") || command.quantity() < 1 || command.quantity() > 1_000_000) {
            throw new IllegalArgumentException("A valid SKU and quantity between 1 and 1000000 are required");
        }
        Command normalized = new Command(
                tenantId, actorId, command.orderId(), sku, command.quantity(), correlationId, idempotencyKey);
        stock.acquireReservationLocks(tenantId, command.orderId(), sku, idempotencyKey);
        var replay = stock.findReservationByIdempotencyKey(tenantId, idempotencyKey);
        if (replay.isPresent()) {
            assertSameReservation(replay.get(), normalized);
            return replay.get();
        }
        var existingLine = stock.findReservationByOrderLine(tenantId, command.orderId(), sku);
        if (existingLine.isPresent()) {
            assertSameReservation(existingLine.get(), normalized);
            return existingLine.get();
        }
        if (!stock.decrementAvailableStock(tenantId, sku, command.quantity())) {
            throw new IllegalStateException("Stock is missing or insufficient for reservation");
        }
        Reservation reservation = new Reservation(
                UUID.randomUUID(), command.orderId(), sku, command.quantity(), Status.RESERVED, clock.instant());
        stock.saveReservation(reservation, normalized);
        return reservation;
    }

    private void assertSameReservation(Reservation existing, Command command) {
        if (!existing.orderId().equals(command.orderId()) || !existing.sku().equals(command.sku())
                || existing.quantity() != command.quantity()) {
            throw new IllegalArgumentException("Reservation key was already used for different stock");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }
}
