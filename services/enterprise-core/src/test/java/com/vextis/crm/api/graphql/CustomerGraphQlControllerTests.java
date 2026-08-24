package com.vextis.crm.api.graphql;

import com.vextis.crm.CustomerAdministration;
import com.vextis.crm.CustomerDirectory;
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

@GraphQlTest(CustomerGraphQlController.class)
class CustomerGraphQlControllerTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private CustomerAdministration customers;

    @MockitoBean
    private CurrentActorProvider actors;

    @Test
    void savesCustomerForAuthenticatedActorAndDemoTenant() {
        when(actors.currentActorId()).thenReturn("firebase-user-123");
        when(customers.save(any())).thenReturn(
                new CustomerDirectory.CustomerSummary(CUSTOMER_ID, "Acme Colombia", true));

        graphQlTester.document("""
                        mutation SaveCustomer($input: UpsertCustomerInput!) {
                          upsertCustomer(input: $input) { id legalName active }
                        }
                        """)
                .variable("input", Map.of("legalName", "Acme Colombia", "active", true))
                .execute()
                .path("upsertCustomer.id").entity(String.class).isEqualTo(CUSTOMER_ID.toString())
                .path("upsertCustomer.active").entity(Boolean.class).isEqualTo(true);

        ArgumentCaptor<CustomerAdministration.SaveCustomerCommand> command =
                ArgumentCaptor.forClass(CustomerAdministration.SaveCustomerCommand.class);
        verify(customers).save(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("demo-tenant");
        assertThat(command.getValue().actorId()).isEqualTo("firebase-user-123");
    }
}
