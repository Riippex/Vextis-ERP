package com.vextis.inventory;

import java.util.List;
import java.util.UUID;

public interface ReservationDirectory {

    List<StockReservation.Reservation> findAll(String tenantId);

    List<StockReservation.Reservation> findByOrder(String tenantId, UUID orderId);
}
