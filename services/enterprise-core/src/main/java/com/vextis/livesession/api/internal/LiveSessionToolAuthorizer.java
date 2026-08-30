package com.vextis.livesession.api.internal;

import com.vextis.shared.ServiceCallerIdentities;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks the service credential on Live session validation.
 *
 * <p>Unlike every other {@code /internal/agent-tools/**} endpoint there is no
 * tenant to check: Agent Runtime does not know it until the session token has
 * been validated, which is the point of the call. What is checked is that the
 * presented credential resolves to a known service identity and that the caller
 * declares that same identity, so a caller cannot borrow another one.
 */
@Component
class LiveSessionToolAuthorizer {

    private final ServiceCallerIdentities callers;

    LiveSessionToolAuthorizer(ServiceCallerIdentities callers) {
        this.callers = callers;
    }

    void authorize(String authorization, String agentId) {
        if (!callers.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Live session validation is not configured");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing service credential");
        }
        String serviceIdentity = callers.resolve(authorization.substring("Bearer ".length()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid service credential"));

        if (!serviceIdentity.equals(agentId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Agent is not authorized for this tool");
        }
    }
}
