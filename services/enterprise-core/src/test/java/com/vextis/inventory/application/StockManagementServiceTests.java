package com.vextis.inventory.application;

import com.vextis.audit.AuditTrail;
import com.vextis.inventory.StockAdministration;
import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.StockReservation;
import com.vextis.inventory.application.port.StockRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockManagementServiceTests {

    private final StockRepository repository = mock(StockRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final StockManagementService service = new StockManagementService(
            repository,
            audit,
            Clock.fixed(Instant.parse("2026-08-24T19:00:00Z"), ZoneOffset.UTC));

    @Test
    void normalizesSkuAndAuditsAvailabilityChange() {
        when(repository.setAvailability("demo-tenant", "VXT-CHAIR-01", 40)).thenReturn(
                new StockDirectory.StockSummary("VXT-CHAIR-01", 40));

        StockDirectory.StockSummary result = service.setAvailability(
                new StockAdministration.SetAvailabilityCommand(
                        "demo-tenant", "firebase-user-123", "vxt-chair-01", 40));

        assertThat(result.sku()).isEqualTo("VXT-CHAIR-01");
        verify(audit).recordUserAction(org.mockito.ArgumentMatchers.argThat(action ->
                action.actorId().equals("firebase-user-123")
                        && action.action().equals("inventory.stock.availability-set")));
    }

    @Test
    void rejectsNegativeAvailabilityBeforePersistence() {
        assertThatThrownBy(() -> service.setAvailability(new StockAdministration.SetAvailabilityCommand(
                "demo-tenant", "firebase-user-123", "VXT-CHAIR-01", -1)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, audit);
    }

    @Test
    void reservesNormalizedStockAndPersistsAuditReadyContext() {
        UUID orderId = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
        when(repository.findReservationByIdempotencyKey("demo-tenant", "approval-event:reserve:chair"))
                .thenReturn(java.util.Optional.empty());
        when(repository.findReservationByOrderLine("demo-tenant", orderId, "VXT-CHAIR-01"))
                .thenReturn(java.util.Optional.empty());
        when(repository.decrementAvailableStock("demo-tenant", "VXT-CHAIR-01", 10)).thenReturn(true);

        StockReservation.Reservation result = service.reserve(new StockReservation.Command(
                " demo-tenant ", "coordinator-agent", orderId, "vxt-chair-01", 10,
                "corr-001", "approval-event:reserve:chair"));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.sku()).isEqualTo("VXT-CHAIR-01");
        assertThat(result.status()).isEqualTo(StockReservation.Status.RESERVED);
        verify(repository).acquireReservationLocks(
                "demo-tenant", orderId, "VXT-CHAIR-01", "approval-event:reserve:chair");
        verify(repository).saveReservation(
                org.mockito.ArgumentMatchers.eq(result),
                org.mockito.ArgumentMatchers.argThat(command ->
                        command.actorId().equals("coordinator-agent")
                                && command.correlationId().equals("corr-001")));
    }

    @Test
    void returnsIdempotentReservationWithoutDecrementingStockAgain() {
        UUID orderId = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
        StockReservation.Reservation existing = new StockReservation.Reservation(
                UUID.randomUUID(), orderId, "VXT-CHAIR-01", 10,
                StockReservation.Status.RESERVED, Instant.parse("2026-08-24T18:00:00Z"));
        when(repository.findReservationByIdempotencyKey("demo-tenant", "approval-event:reserve:chair"))
                .thenReturn(java.util.Optional.of(existing));

        StockReservation.Reservation result = service.reserve(new StockReservation.Command(
                "demo-tenant", "coordinator-agent", orderId, "VXT-CHAIR-01", 10,
                "corr-001", "approval-event:reserve:chair"));

        assertThat(result).isEqualTo(existing);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .decrementAvailableStock(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsInsufficientStockWithoutSavingReservation() {
        UUID orderId = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
        when(repository.findReservationByIdempotencyKey("demo-tenant", "approval-event:reserve:chair"))
                .thenReturn(java.util.Optional.empty());
        when(repository.findReservationByOrderLine("demo-tenant", orderId, "VXT-CHAIR-01"))
                .thenReturn(java.util.Optional.empty());
        when(repository.decrementAvailableStock("demo-tenant", "VXT-CHAIR-01", 10)).thenReturn(false);

        assertThatThrownBy(() -> service.reserve(new StockReservation.Command(
                "demo-tenant", "coordinator-agent", orderId, "VXT-CHAIR-01", 10,
                "corr-001", "approval-event:reserve:chair")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stock is missing or insufficient for reservation");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).saveReservation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
