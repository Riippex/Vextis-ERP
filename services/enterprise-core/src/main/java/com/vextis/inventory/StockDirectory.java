package com.vextis.inventory;

import java.util.List;

public interface StockDirectory {

    List<StockSummary> findAll(String tenantId);

    record StockSummary(String sku, int availableQuantity) {
    }
}
