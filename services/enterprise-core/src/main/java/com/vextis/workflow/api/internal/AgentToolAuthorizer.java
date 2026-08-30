package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.shared.ServiceCallerIdentities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class AgentToolAuthorizer {

    private final ServiceCallerIdentities callers;
    private final String allowedTenantId;
    private final AgentDirectory agents;

    AgentToolAuthorizer(
            ServiceCallerIdentities callers,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String allowedTenantId,
            AgentDirectory agents
    ) {
        this.callers = callers;
        this.allowedTenantId = allowedTenantId;
        this.agents = agents;
    }

    void authorize(String authorization, String agentId, String tenantId, AgentTool tool) {
        if (!callers.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Agent tools are disabled until a service credential is configured"
            );
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing service credential");
        }
        // Which credential was presented decides which service identity is
        // calling, and the registry decides what that identity may do. The
        // public Live gateway therefore cannot reach a mutating tool even with
        // a valid credential of its own.
        String serviceIdentity = callers.resolve(authorization.substring("Bearer ".length()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid service credential"));

        if (!allowedTenantId.equals(tenantId)) {
            throw forbidden();
        }
        boolean authorized = agents.findActive(tenantId, agentId)
                .filter(registration -> serviceIdentity.equals(registration.serviceIdentity()))
                .filter(registration -> registration.allowedTools().contains(tool.policyName()))
                .isPresent();
        if (!authorized) {
            throw forbidden();
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent is not authorized for tool or tenant");
    }
}
