package com.vextis.inventory.application;

import com.vextis.audit.AuditTrail;
import com.vextis.inventory.StockAdministration;
import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.application.port.StockRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
}
