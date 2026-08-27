package com.vextis.billing.api.graphql;

import com.vextis.billing.Invoice;

import java.util.List;
import java.util.UUID;

public record InvoiceView(
        UUID id, UUID orderId, UUID executionId, String customerName, String currency,
        String subtotal, String tax, String total, String status, int paymentTermsDays,
        String issuedAt, String correlationId, List<InvoiceLineView> lines
) {
    public static InvoiceView from(Invoice invoice) {
        return new InvoiceView(
                invoice.id(), invoice.orderId(), invoice.executionId(), invoice.customerName(), invoice.currency(),
                invoice.subtotal().toPlainString(), invoice.tax().toPlainString(), invoice.total().toPlainString(),
                invoice.status().name(), invoice.paymentTermsDays(), invoice.issuedAt().toString(),
                invoice.correlationId(), invoice.lines().stream().map(InvoiceLineView::from).toList());
    }

    public record InvoiceLineView(String sku, int quantity, String unitPrice, String lineSubtotal) {
        static InvoiceLineView from(Invoice.Line line) {
            return new InvoiceLineView(
                    line.sku(), line.quantity(), line.unitPrice().toPlainString(), line.lineSubtotal().toPlainString());
        }
    }
}
