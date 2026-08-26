package com.vextis.missioncontrol.api.graphql;

import com.vextis.agentregistry.AgentDirectory;
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
    private AgentDirectory agents;

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
        when(agents.findAll(eq("demo-tenant"))).thenReturn(List.of());
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

    @Test
    @WithMockUser(username = "firebase-user-123")
    void returnsTheTenantScopedApprovedAgentRegistry() {
        when(agents.findAll(eq("demo-tenant"))).thenReturn(List.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_inventory_agent",
                        "1.0.0",
                        "Inventory Agent",
                        "INVENTORY_OPERATIONS",
                        "Provides authoritative SKU availability.",
                        "GOOGLE_ADK",
                        "gemini-3.5-flash",
                        "1.0.0",
                        "coordinator-agent",
                        "ACTIVE",
                        List.of("stock lookup"),
                        List.of("get_stock")
                )
        ));

        graphQlTester.document("""
                        query AgentRegistry {
                          missionControl {
                            agents {
                              agentId version displayName department purpose framework modelId
                              promptVersion serviceIdentity status capabilities allowedTools
                            }
                          }
                        }
                        """)
                .execute()
                .path("missionControl.agents[0].agentId")
                .entity(String.class)
                .isEqualTo("vextis_inventory_agent")
                .path("missionControl.agents[0].allowedTools[0]")
                .entity(String.class)
                .isEqualTo("get_stock")
                .path("missionControl.agents[0].modelId")
                .entity(String.class)
                .isEqualTo("gemini-3.5-flash");
    }
}
