package com.vextis.workflow.application;

import com.vextis.inventory.StockReservation;

public interface ReserveApprovedStockUseCase {

    StockReservation.Reservation reserve(ReserveApprovedStockCommand command);
}
