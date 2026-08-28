package com.vextis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the tool allowlist each demo agent ends up with after every Flyway
 * migration has been applied, not after the most recently written one.
 */
class AgentToolPermissionMigrationTests {

    private static final String DEMO_TENANT = "demo-tenant";

    private final Map<String, List<String>> allowedToolsByAgent =
            AgentRegistryMigrationReplay.activeAllowedToolsByAgent(DEMO_TENANT);

    @Test
    void coordinatorKeepsPlanningApprovalAndKnowledgeTools() {
        assertThat(allowedToolsByAgent.get("vextis_coordinator"))
                .containsExactlyInAnyOrder(
                        "start_execution_planning",
                        "record_execution_plan",
                        "evaluate_order_readiness",
                        "request_workflow_approval",
                        "search_knowledge_base");
    }

    @Test
    void inventoryAgentKeepsStockReadReservationAndKnowledgeTools() {
        assertThat(allowedToolsByAgent.get("vextis_inventory_agent"))
                .containsExactlyInAnyOrder("get_stock", "reserve_stock", "search_knowledge_base");
    }

    @Test
    void crmAgentKeepsCustomerLookupAndKnowledgeTools() {
        assertThat(allowedToolsByAgent.get("vextis_crm_agent"))
                .containsExactlyInAnyOrder("lookup_customer", "search_knowledge_base");
    }

    @Test
    void billingAgentKeepsCreditInvoiceAndKnowledgeTools() {
        assertThat(allowedToolsByAgent.get("vextis_billing_agent"))
                .containsExactlyInAnyOrder("get_credit", "create_invoice", "search_knowledge_base");
    }

    @Test
    void everyDemoAgentKeepsAtLeastOneTool() {
        assertThat(allowedToolsByAgent)
                .isNotEmpty()
                .allSatisfy((agentId, tools) ->
                        assertThat(tools).as("tools granted to %s", agentId).isNotEmpty());
    }

    @Test
    void onlyOneActiveVersionExistsPerAgent() {
        List<AgentRegistryMigrationReplay.Registration> active =
                AgentRegistryMigrationReplay.replay().stream()
                        .filter(row -> DEMO_TENANT.equals(row.tenantId()))
                        .filter(row -> "ACTIVE".equals(row.status()))
                        .toList();

        assertThat(active)
                .extracting(AgentRegistryMigrationReplay.Registration::agentId)
                .doesNotHaveDuplicates();
    }
}
