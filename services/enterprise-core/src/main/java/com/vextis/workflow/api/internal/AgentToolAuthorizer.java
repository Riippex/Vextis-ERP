package com.vextis.workflow.api.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
class AgentToolAuthorizer {

    private final String serviceToken;
    private final String allowedAgentId;
    private final String allowedTenantId;

    AgentToolAuthorizer(
            @Value("${vextis.agent-tools.service-token:}") String serviceToken,
            @Value("${vextis.agent-tools.coordinator-agent-id:coordinator-agent}") String allowedAgentId,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String allowedTenantId
    ) {
        this.serviceToken = serviceToken;
        this.allowedAgentId = allowedAgentId;
        this.allowedTenantId = allowedTenantId;
    }

    void authorize(String authorization, String agentId, String tenantId) {
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
        if (!allowedAgentId.equals(agentId) || !allowedTenantId.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent is not authorized for tenant");
        }
    }
}
