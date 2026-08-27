package com.vextis.billing.application;

import com.vextis.billing.Invoice;
import com.vextis.billing.InvoiceIssuer;
import com.vextis.billing.application.port.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceManagementServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-27T18:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");

    private final InvoiceRepository repository = mock(InvoiceRepository.class);
    private final InvoiceManagementService service = new InvoiceManagementService(
            repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void calculatesAndPersistsAuthoritativeTotals() {
        InvoiceIssuer.IssueCommand command = command("invoice-event-0001");

        Invoice invoice = service.issue(command);

        assertThat(invoice.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(invoice.tax()).isEqualByComparingTo("190.00");
        assertThat(invoice.total()).isEqualByComparingTo("1190.00");
        assertThat(invoice.status()).isEqualTo(Invoice.Status.ISSUED);
        assertThat(invoice.issuedAt()).isEqualTo(NOW);
        verify(repository).save(invoice, command);
    }

    @Test
    void returnsOriginalInvoiceForRetriedOrderWithoutDuplicateBilling() {
        Invoice existing = existingInvoice();
        when(repository.findByIdempotencyKey("demo-tenant", "invoice-event-0001"))
                .thenReturn(Optional.of(existing));

        Invoice replay = service.issue(command("invoice-event-0001"));

        assertThat(replay).isSameAs(existing);
        verify(repository, never()).save(any(), any());
    }

    @Test
    void rejectsASecondIdempotencyKeyForAnAlreadyInvoicedOrder() {
        when(repository.findByOrder("demo-tenant", ORDER_ID)).thenReturn(Optional.of(existingInvoice()));

        assertThatThrownBy(() -> service.issue(command("invoice-event-0002")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different idempotency key");
        verify(repository, never()).save(any(), any());
    }

    @Test
    void rejectsComputedAmountsOutsideTheDatabaseRange() {
        InvoiceIssuer.IssueCommand oversized = new InvoiceIssuer.IssueCommand(
                "demo-tenant", "vextis_billing_agent", ORDER_ID, EXECUTION_ID, "Acme Colombia", "COP",
                List.of(new InvoiceIssuer.LineInput(
                        "VXT-CHAIR-01", 2, new BigDecimal("99999999999999999.99"))),
                30, "corr-001", "invoice-event-0001");

        assertThatThrownBy(() -> service.issue(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported monetary range");
        verify(repository, never()).save(any(), any());
    }

    @Test
    void rejectsUnpricedOrOverPreciseLines() {
        InvoiceIssuer.IssueCommand unpriced = new InvoiceIssuer.IssueCommand(
                "demo-tenant", "vextis_billing_agent", ORDER_ID, EXECUTION_ID, "Acme Colombia", "COP",
                List.of(new InvoiceIssuer.LineInput("VXT-CHAIR-01", 10, null)), 30, "corr-001",
                "invoice-event-0001");

        assertThatThrownBy(() -> service.issue(unpriced))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit price");
    }

    private InvoiceIssuer.IssueCommand command(String idempotencyKey) {
        return new InvoiceIssuer.IssueCommand(
                "demo-tenant", "vextis_billing_agent", ORDER_ID, EXECUTION_ID, "Acme Colombia", "COP",
                List.of(new InvoiceIssuer.LineInput("VXT-CHAIR-01", 10, new BigDecimal("100.00"))),
                30, "corr-001", idempotencyKey);
    }

    private Invoice existingInvoice() {
        return new Invoice(
                UUID.fromString("3e2fb128-12e8-48fa-acdd-4748e00657ef"), ORDER_ID, EXECUTION_ID,
                "Acme Colombia", "COP", new BigDecimal("1000.00"), new BigDecimal("190.00"),
                new BigDecimal("1190.00"), Invoice.Status.ISSUED, 30, NOW, "corr-001",
                List.of(new Invoice.Line(
                        "VXT-CHAIR-01", 10, new BigDecimal("100.00"), new BigDecimal("1000.00"))));
    }
}
