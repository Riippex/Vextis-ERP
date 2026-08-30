package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.shared.ServiceCallerIdentities;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentToolAuthorizerTests {

    /** Two credentials, two service identities, as a real deployment now has. */
    private static final ServiceCallerIdentities CALLERS = new ServiceCallerIdentities() {
        private final Map<String, String> identities = Map.of(
                "service-secret", "coordinator-agent",
                "live-gateway-secret", "live-gateway-agent");

        @Override
        public Optional<String> resolve(String presentedToken) {
            return Optional.ofNullable(identities.get(presentedToken));
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    };

    private final AgentDirectory agents = mock(AgentDirectory.class);
    private final AgentToolAuthorizer authorizer =
            new AgentToolAuthorizer(CALLERS, "demo-tenant", agents);

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

    @Test
    void serviceUnavailableWhenNoCredentialIsConfigured() {
        AgentToolAuthorizer unconfigured = new AgentToolAuthorizer(new ServiceCallerIdentities() {
            @Override
            public Optional<String> resolve(String presentedToken) {
                return Optional.empty();
            }

            @Override
            public boolean isConfigured() {
                return false;
            }
        }, "demo-tenant", agents);

        assertThatThrownBy(() -> unconfigured.authorize(
                "Bearer anything", "vextis_inventory_agent", "demo-tenant", AgentTool.GET_STOCK))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void liveGatewayCredentialReachesItsOwnReadOnlyAgent() {
        when(agents.findActive("demo-tenant", "vextis_live_inventory_agent"))
                .thenReturn(Optional.of(registration("live-gateway-agent", List.of("get_stock"))));

        authorizer.authorize(
                "Bearer live-gateway-secret", "vextis_live_inventory_agent", "demo-tenant",
                AgentTool.GET_STOCK);
    }

    @Test
    void liveGatewayCredentialCannotReachAMutatingTool() {
        // The whole point of the separate credential: a compromise of the public
        // gateway must not be able to reserve stock or issue an invoice.
        when(agents.findActive("demo-tenant", "vextis_live_inventory_agent"))
                .thenReturn(Optional.of(registration("live-gateway-agent", List.of("get_stock"))));

        assertForbidden(() -> authorizer.authorize(
                "Bearer live-gateway-secret", "vextis_live_inventory_agent", "demo-tenant",
                AgentTool.RESERVE_STOCK));
    }

    @Test
    void liveGatewayCredentialCannotBorrowThePrivateRuntimeAgents() {
        // vextis_inventory_agent belongs to coordinator-agent, so presenting the
        // gateway credential for it is a different principal asking.
        when(agents.findActive("demo-tenant", "vextis_inventory_agent"))
                .thenReturn(Optional.of(registration("coordinator-agent", List.of("get_stock", "reserve_stock"))));

        assertForbidden(() -> authorizer.authorize(
                "Bearer live-gateway-secret", "vextis_inventory_agent", "demo-tenant",
                AgentTool.RESERVE_STOCK));
    }

    @Test
    void privateRuntimeCredentialCannotBorrowTheLiveAgents() {
        when(agents.findActive("demo-tenant", "vextis_live_inventory_agent"))
                .thenReturn(Optional.of(registration("live-gateway-agent", List.of("get_stock"))));

        assertForbidden(() -> authorizer.authorize(
                "Bearer service-secret", "vextis_live_inventory_agent", "demo-tenant",
                AgentTool.GET_STOCK));
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
