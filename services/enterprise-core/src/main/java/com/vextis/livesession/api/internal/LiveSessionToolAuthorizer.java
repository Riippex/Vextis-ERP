package com.vextis.livesession.api.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Same opaque-service-token check as the workflow module's AgentToolAuthorizer
 * (reusing the shared VEXTIS_AGENT_TOOLS_TOKEN secret, per the OpenAPI
 * contract's serviceBearer scheme), duplicated locally rather than reused
 * across the module boundary: Agent Runtime does not know the tenant before
 * the session token is validated, so there is no tenant to check here, unlike
 * every other /internal/agent-tools/** endpoint.
 */
@Component
class LiveSessionToolAuthorizer {

    private final String serviceToken;
    private final String allowedAgentId;

    LiveSessionToolAuthorizer(
            @Value("${vextis.agent-tools.service-token:}") String serviceToken,
            @Value("${vextis.agent-tools.coordinator-agent-id:coordinator-agent}") String allowedAgentId
    ) {
        this.serviceToken = serviceToken;
        this.allowedAgentId = allowedAgentId;
    }

    void authorize(String authorization, String agentId) {
        if (serviceToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Live session validation is not configured");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing service credential");
        }
        byte[] presented = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = serviceToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
        if (!allowedAgentId.equals(agentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent is not authorized for this tool");
        }
    }
}
