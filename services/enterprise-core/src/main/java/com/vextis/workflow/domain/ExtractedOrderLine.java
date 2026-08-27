package com.vextis.workflow.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ExtractedOrderLine(String sku, int quantity, BigDecimal unitPrice) {
    public ExtractedOrderLine(String sku, int quantity) {
        this(sku, quantity, null);
    }

    public ExtractedOrderLine {
        if (sku == null || sku.isBlank() || sku.length() > 100 || !sku.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Order line SKU is invalid");
        }
        if (quantity < 1 || quantity > 1_000_000) {
            throw new IllegalArgumentException("Order line quantity is invalid");
        }
        if (unitPrice != null) {
            try {
                unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Order line unit price must have at most two decimals", exception);
            }
            if (unitPrice.signum() <= 0 || unitPrice.precision() > 19) {
                throw new IllegalArgumentException("Order line unit price must be positive and fit NUMERIC(19,2)");
            }
        }
        sku = sku.trim().toUpperCase();
    }
}
