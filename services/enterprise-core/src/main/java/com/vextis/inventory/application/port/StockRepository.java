package com.vextis.inventory.application.port;

import com.vextis.inventory.StockDirectory;

public interface StockRepository {

    StockDirectory.StockSummary setAvailability(String tenantId, String sku, int availableQuantity);
}
