package com.vextis.crm.application;

import com.vextis.audit.AuditTrail;
import com.vextis.crm.CustomerAdministration;
import com.vextis.crm.CustomerDirectory;
import com.vextis.crm.application.port.CustomerRepository;
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

class CustomerManagementServiceTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-08-24T19:00:00Z");

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final CustomerManagementService service = new CustomerManagementService(
            repository, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsTrimmedCustomerAndAuditsActor() {
        when(repository.save("demo-tenant", null, "Acme Colombia", true)).thenReturn(
                new CustomerDirectory.CustomerSummary(CUSTOMER_ID, "Acme Colombia", true));

        CustomerDirectory.CustomerSummary result = service.save(new CustomerAdministration.SaveCustomerCommand(
                "demo-tenant", "firebase-user-123", null, "  Acme Colombia  ", true));

        assertThat(result.id()).isEqualTo(CUSTOMER_ID);
        verify(audit).recordUserAction(org.mockito.ArgumentMatchers.argThat(action ->
                action.actorId().equals("firebase-user-123")
                        && action.action().equals("crm.customer.saved")
                        && action.resourceId().equals(CUSTOMER_ID)));
    }

    @Test
    void rejectsBlankLegalNameBeforePersistence() {
        assertThatThrownBy(() -> service.save(new CustomerAdministration.SaveCustomerCommand(
                "demo-tenant", "firebase-user-123", null, " ", true)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, audit);
    }
}
