package com.vextis.inventory;

import java.util.Optional;

public interface StockLookup {
    Optional<StockSnapshot> findBySku(String tenantId, String sku);

    record StockSnapshot(String sku, int availableQuantity) {
    }
}
