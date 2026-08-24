package com.vextis.workflow.domain;

public record ExtractedOrderLine(String sku, int quantity) {
    public ExtractedOrderLine {
        if (sku == null || sku.isBlank() || sku.length() > 100 || !sku.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Order line SKU is invalid");
        }
        if (quantity < 1 || quantity > 1_000_000) {
            throw new IllegalArgumentException("Order line quantity is invalid");
        }
        sku = sku.trim().toUpperCase();
    }
}
