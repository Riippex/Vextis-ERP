package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentToolAuthorizerTests {

    private final AgentDirectory agents = mock(AgentDirectory.class);
    private final AgentToolAuthorizer authorizer = new AgentToolAuthorizer(
            "service-secret", "coordinator-agent", "demo-tenant", agents);

    @Test
    void allowsExactToolForActiveAgentBoundToAuthenticatedService() {
        when(agents.findActive("demo-tenant", "vextis_inventory_agent"))
                .thenReturn(Optional.of(registration("coordinator-agent", List.of("get_stock"))));

        authorizer.authorize(
                "Bearer service-secret", "vextis_inventory_agent", "demo-tenant", AgentTool.GET_STOCK);
    }

    @Test
    void rejectsToolOutsideLogicalAgentAllowlist() {
        when(agents.findActive("demo-tenant", "vextis_crm_agent"))
                .thenReturn(Optional.of(registration("coordinator-agent", List.of("lookup_customer"))));

        assertForbidden(() -> authorizer.authorize(
                "Bearer service-secret", "vextis_crm_agent", "demo-tenant", AgentTool.GET_STOCK));
    }

    @Test
    void rejectsAgentRegisteredToAnotherServiceIdentity() {
        when(agents.findActive("demo-tenant", "vextis_inventory_agent"))
                .thenReturn(Optional.of(registration("other-runtime", List.of("get_stock"))));

        assertForbidden(() -> authorizer.authorize(
                "Bearer service-secret", "vextis_inventory_agent", "demo-tenant", AgentTool.GET_STOCK));
    }

    @Test
    void rejectsUnknownOrInactiveAgent() {
        when(agents.findActive("demo-tenant", "retired-agent")).thenReturn(Optional.empty());

        assertForbidden(() -> authorizer.authorize(
                "Bearer service-secret", "retired-agent", "demo-tenant", AgentTool.GET_STOCK));
    }

    @Test
    void rejectsInvalidCredentialBeforeReadingPolicy() {
        assertThatThrownBy(() -> authorizer.authorize(
                "Bearer wrong", "vextis_inventory_agent", "demo-tenant", AgentTool.GET_STOCK))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(agents);
    }

    @Test
    void rejectsForeignTenantBeforeReadingPolicy() {
        assertForbidden(() -> authorizer.authorize(
                "Bearer service-secret", "vextis_inventory_agent", "other-tenant", AgentTool.GET_STOCK));

        verifyNoInteractions(agents);
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private AgentDirectory.AgentRegistration registration(String serviceIdentity, List<String> allowedTools) {
        return new AgentDirectory.AgentRegistration(
                "agent", "1.0.0", "Agent", "INVENTORY_OPERATIONS", "purpose", "GOOGLE_ADK",
                "gemini-3.5-flash", "1.0.0", serviceIdentity, "ACTIVE", List.of(), allowedTools);
    }
}
