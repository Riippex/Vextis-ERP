package com.vextis.inventory;

import java.util.List;

public interface ReservationDirectory {

    List<StockReservation.Reservation> findAll(String tenantId);
}
