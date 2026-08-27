package com.vextis.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Invoice(
        UUID id,
        UUID orderId,
        UUID executionId,
        String customerName,
        String currency,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        Status status,
        int paymentTermsDays,
        Instant issuedAt,
        String correlationId,
        List<Line> lines
) {
    public Invoice {
        if (id == null || orderId == null || executionId == null || status == null || issuedAt == null) {
            throw new IllegalArgumentException("Invoice identity, status and issue time are required");
        }
        if (customerName == null || customerName.isBlank() || customerName.trim().length() > 200) {
            throw new IllegalArgumentException("Invoice customer name is required");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invoice currency must be an ISO 4217 code");
        }
        if (subtotal == null || tax == null || total == null || subtotal.signum() <= 0 || tax.signum() < 0
                || total.compareTo(subtotal.add(tax)) != 0) {
            throw new IllegalArgumentException("Invoice totals are invalid");
        }
        if (paymentTermsDays < 0 || paymentTermsDays > 365) {
            throw new IllegalArgumentException("Invoice payment terms must be between 0 and 365 days");
        }
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
            throw new IllegalArgumentException("Invoice correlation id is required");
        }
        if (lines == null || lines.isEmpty() || lines.size() > 20) {
            throw new IllegalArgumentException("Invoice must contain between 1 and 20 lines");
        }
        customerName = customerName.trim();
        lines = List.copyOf(lines);
    }

    public enum Status {
        ISSUED
    }

    public record Line(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineSubtotal) {
        public Line {
            if (sku == null || sku.isBlank() || !sku.matches("[A-Z0-9._-]+") || sku.length() > 100) {
                throw new IllegalArgumentException("Invoice line SKU is invalid");
            }
            if (quantity < 1 || quantity > 1_000_000 || unitPrice == null || unitPrice.signum() <= 0
                    || lineSubtotal == null || lineSubtotal.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
                throw new IllegalArgumentException("Invoice line quantity or amounts are invalid");
            }
        }
    }
}
