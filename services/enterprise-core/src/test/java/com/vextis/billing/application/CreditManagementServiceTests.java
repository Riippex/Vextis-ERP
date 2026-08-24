package com.vextis.billing.application;

import com.vextis.audit.AuditTrail;
import com.vextis.billing.CreditAdministration;
import com.vextis.billing.CreditLookup;
import com.vextis.billing.CreditPortfolio;
import com.vextis.billing.application.port.CreditRepository;
import com.vextis.crm.CustomerDirectory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreditManagementServiceTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final CreditRepository repository = mock(CreditRepository.class);
    private final CustomerDirectory customers = mock(CustomerDirectory.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final CreditManagementService service = new CreditManagementService(
            repository,
            customers,
            audit,
            Clock.fixed(Instant.parse("2026-08-24T19:00:00Z"), ZoneOffset.UTC));

    @Test
    void savesCreditOnlyForCustomerInTenantAndAuditsChange() {
        when(customers.findById("demo-tenant", CUSTOMER_ID)).thenReturn(Optional.of(
                new CustomerDirectory.CustomerSummary(CUSTOMER_ID, "Acme Colombia", true)));
        when(repository.save("demo-tenant", CUSTOMER_ID, CreditLookup.CreditStanding.GOOD, 30)).thenReturn(
                new CreditPortfolio.CreditProfileSummary(CUSTOMER_ID, "GOOD", 30));

        CreditAdministration.SavedCreditProfile result = service.save(
                new CreditAdministration.SaveCreditProfileCommand(
                        "demo-tenant", "firebase-user-123", CUSTOMER_ID, CreditLookup.CreditStanding.GOOD, 30));

        assertThat(result.standing()).isEqualTo(CreditLookup.CreditStanding.GOOD);
        assertThat(result.customerName()).isEqualTo("Acme Colombia");
        verify(audit).recordUserAction(org.mockito.ArgumentMatchers.argThat(action ->
                action.actorId().equals("firebase-user-123")
                        && action.action().equals("billing.credit-profile.saved")
                        && action.resourceId().equals(CUSTOMER_ID)));
    }

    @Test
    void rejectsCreditForCustomerOutsideTenant() {
        when(customers.findById("demo-tenant", CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(new CreditAdministration.SaveCreditProfileCommand(
                "demo-tenant", "firebase-user-123", CUSTOMER_ID, CreditLookup.CreditStanding.GOOD, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer was not found");
        verifyNoInteractions(repository, audit);
    }
}
