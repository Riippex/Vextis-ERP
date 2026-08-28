package com.vextis.workflow.api.internal;

import com.vextis.AgentRegistryMigrationReplay;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry stores tool names as free text, so a typo in a migration would
 * revoke a permission just as effectively as dropping it. Every name the
 * migrations grant must resolve to a tool {@link AgentToolAuthorizer} can check.
 */
class AgentToolPolicyCoverageTests {

    private static final Set<String> POLICY_NAMES = Arrays.stream(AgentTool.values())
            .map(AgentTool::policyName)
            .collect(Collectors.toUnmodifiableSet());

    @Test
    void everyGrantedToolNameMatchesAnEnforceableTool() {
        List<String> granted = AgentRegistryMigrationReplay.activeAllowedToolsByAgent("demo-tenant")
                .values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        assertThat(granted).isNotEmpty();
        assertThat(POLICY_NAMES).containsAll(granted);
    }

    @Test
    void everyEnforceableToolIsGrantedToAtLeastOneAgent() {
        Set<String> granted = AgentRegistryMigrationReplay.activeAllowedToolsByAgent("demo-tenant")
                .values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(granted).containsAll(POLICY_NAMES);
    }
}
