package com.vextis.inventory.application.port;

import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.StockReservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    StockDirectory.StockSummary setAvailability(String tenantId, String sku, int availableQuantity);

    void acquireReservationLocks(String tenantId, UUID orderId, String sku, String idempotencyKey);

    Optional<StockReservation.Reservation> findReservationByIdempotencyKey(
            String tenantId, String idempotencyKey);

    Optional<StockReservation.Reservation> findReservationByOrderLine(
            String tenantId, UUID orderId, String sku);

    boolean decrementAvailableStock(String tenantId, String sku, int quantity);

    void saveReservation(StockReservation.Reservation reservation, StockReservation.Command command);

    List<StockReservation.Reservation> findReservations(String tenantId);
}
