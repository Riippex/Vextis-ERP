package com.vextis.inventory;

import java.time.Instant;
import java.util.UUID;

public interface StockReservation {

    Reservation reserve(Command command);

    record Command(
            String tenantId, String actorId, UUID orderId, String sku, int quantity,
            String correlationId, String idempotencyKey
    ) {
    }

    record Reservation(
            UUID id, UUID orderId, String sku, int quantity, Status status, Instant createdAt
    ) {
    }

    enum Status {
        RESERVED,
        RELEASED,
        FULFILLED
    }
}
