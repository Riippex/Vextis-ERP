package com.vextis.inventory;

public interface StockAdministration {

    StockDirectory.StockSummary setAvailability(SetAvailabilityCommand command);

    record SetAvailabilityCommand(String tenantId, String actorId, String sku, int availableQuantity) {
    }
}
