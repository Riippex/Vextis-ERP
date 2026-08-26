package com.vextis.workflow.api.internal;

import com.vextis.agentregistry.AgentDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
class AgentToolAuthorizer {

    private final String serviceToken;
    private final String trustedServiceIdentity;
    private final String allowedTenantId;
    private final AgentDirectory agents;

    AgentToolAuthorizer(
            @Value("${vextis.agent-tools.service-token:}") String serviceToken,
            @Value("${vextis.agent-tools.coordinator-agent-id:coordinator-agent}") String trustedServiceIdentity,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String allowedTenantId,
            AgentDirectory agents
    ) {
        this.serviceToken = serviceToken;
        this.trustedServiceIdentity = trustedServiceIdentity;
        this.allowedTenantId = allowedTenantId;
        this.agents = agents;
    }

    void authorize(String authorization, String agentId, String tenantId, AgentTool tool) {
        if (serviceToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Agent tools are disabled until a service credential is configured"
            );
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing service credential");
        }
        byte[] presented = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = serviceToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
        if (!allowedTenantId.equals(tenantId)) {
            throw forbidden();
        }
        boolean authorized = agents.findActive(tenantId, agentId)
                .filter(registration -> trustedServiceIdentity.equals(registration.serviceIdentity()))
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
