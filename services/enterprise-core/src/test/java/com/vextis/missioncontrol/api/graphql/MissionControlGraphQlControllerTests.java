package com.vextis.missioncontrol.api.graphql;

import com.vextis.billing.CreditPortfolio;
import com.vextis.crm.CustomerDirectory;
import com.vextis.inventory.ReservationDirectory;
import com.vextis.inventory.StockDirectory;
import com.vextis.workflow.ExecutionOverview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@GraphQlTest(MissionControlGraphQlController.class)
@TestPropertySource(properties = "vextis.exposure=PUBLIC")
class MissionControlGraphQlControllerTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private ExecutionOverview executions;

    @MockitoBean
    private CustomerDirectory customers;

    @MockitoBean
    private StockDirectory stock;

    @MockitoBean
    private ReservationDirectory reservations;

    @MockitoBean
    private CreditPortfolio credit;

    @Test
    @WithMockUser(username = "firebase-user-123")
    void returnsExecutionVolumeGroupedByDepartment() {
        when(executions.findRecent(eq("demo-tenant"), eq(12))).thenReturn(List.of());
        when(customers.findAll(eq("demo-tenant"))).thenReturn(List.of());
        when(stock.findAll(eq("demo-tenant"))).thenReturn(List.of());
        when(reservations.findAll(eq("demo-tenant"))).thenReturn(List.of());
        when(credit.findAll(eq("demo-tenant"))).thenReturn(List.of());
        when(executions.volumeByDepartment(eq("demo-tenant"))).thenReturn(List.of(
                new ExecutionOverview.DepartmentVolume("CRM_SALES", 3),
                new ExecutionOverview.DepartmentVolume("INVENTORY_OPERATIONS", 1)
        ));

        graphQlTester.document("""
                        query MissionControlDepartmentVolume {
                          missionControl {
                            executionVolumeByDepartment { department count }
                          }
                        }
                        """)
                .execute()
                .path("missionControl.executionVolumeByDepartment[0].department")
                .entity(String.class)
                .isEqualTo("CRM_SALES")
                .path("missionControl.executionVolumeByDepartment[0].count")
                .entity(Integer.class)
                .isEqualTo(3)
                .path("missionControl.executionVolumeByDepartment[1].department")
                .entity(String.class)
                .isEqualTo("INVENTORY_OPERATIONS");
    }
}
