package com.vextis.billing.api.graphql;

import com.vextis.billing.CreditAdministration;
import com.vextis.billing.CreditLookup;
import com.vextis.shared.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(CreditGraphQlController.class)
class CreditGraphQlControllerTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private CreditAdministration credit;

    @MockitoBean
    private CurrentActorProvider actors;

    @Test
    void savesCreditForExistingTenantCustomer() {
        when(actors.currentActorId()).thenReturn("firebase-user-123");
        when(credit.save(any())).thenReturn(
                new CreditAdministration.SavedCreditProfile(
                        CUSTOMER_ID, "Acme Colombia", CreditLookup.CreditStanding.GOOD, 30));

        graphQlTester.document("""
                        mutation SaveCredit($input: UpsertCreditProfileInput!) {
                          upsertCreditProfile(input: $input) {
                            customerId customerName standing maxPaymentTermsDays
                          }
                        }
                        """)
                .variable("input", Map.of(
                        "customerId", CUSTOMER_ID.toString(),
                        "standing", "GOOD",
                        "maxPaymentTermsDays", 30))
                .execute()
                .path("upsertCreditProfile.customerName").entity(String.class).isEqualTo("Acme Colombia")
                .path("upsertCreditProfile.standing").entity(String.class).isEqualTo("GOOD");

        ArgumentCaptor<CreditAdministration.SaveCreditProfileCommand> command =
                ArgumentCaptor.forClass(CreditAdministration.SaveCreditProfileCommand.class);
        verify(credit).save(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("demo-tenant");
        assertThat(command.getValue().actorId()).isEqualTo("firebase-user-123");
    }
}
