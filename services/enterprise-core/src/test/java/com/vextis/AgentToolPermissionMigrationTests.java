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
                .containsExactlyInAnyOrder("lookup_customer", "register_quote_asset", "search_knowledge_base");
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
    void theLiveGatewayIdentityHoldsOnlyReadTools() {
        // Separating the public gateway credential is only worth something if
        // the identity it resolves to can do less. Anything that mutates
        // business state must stay with coordinator-agent.
        List<String> mutatingTools = List.of(
                "start_execution_planning",
                "record_execution_plan",
                "evaluate_order_readiness",
                "request_workflow_approval",
                "reserve_stock",
                "create_invoice",
                "ingest_knowledge_document");

        assertThat(activeRegistrations("live-gateway-agent"))
                .isNotEmpty()
                .allSatisfy(registration -> assertThat(registration.allowedTools())
                        .as("tools granted to %s", registration.agentId())
                        .doesNotContainAnyElementsOf(mutatingTools));
    }

    @Test
    void everyLiveAgentIsBoundToTheLiveGatewayIdentity() {
        assertThat(AgentRegistryMigrationReplay.replay())
                .filteredOn(registration -> registration.agentId().startsWith("vextis_live_"))
                .isNotEmpty()
                .allSatisfy(registration -> assertThat(registration.serviceIdentity())
                        .isEqualTo("live-gateway-agent"));
    }

    @Test
    void thePrivateRuntimeIdentityKeepsTheMutatingTools() {
        assertThat(activeRegistrations("coordinator-agent"))
                .flatExtracting(AgentRegistryMigrationReplay.Registration::allowedTools)
                .contains("reserve_stock", "create_invoice", "record_execution_plan");
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

    private static List<AgentRegistryMigrationReplay.Registration> activeRegistrations(String serviceIdentity) {
        return AgentRegistryMigrationReplay.replay().stream()
                .filter(row -> DEMO_TENANT.equals(row.tenantId()))
                .filter(row -> "ACTIVE".equals(row.status()))
                .filter(row -> serviceIdentity.equals(row.serviceIdentity()))
                .toList();
    }
}
