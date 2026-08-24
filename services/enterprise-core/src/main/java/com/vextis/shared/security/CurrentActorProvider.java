package com.vextis.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorProvider {

    private final String exposure;
    private final String demoActorId;

    CurrentActorProvider(
            @Value("${vextis.exposure:INTERNAL}") String exposure,
            @Value("${vextis.demo.actor-id:demo-user}") String demoActorId
    ) {
        this.exposure = exposure;
        this.demoActorId = demoActorId;
    }

    public String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        if ("LOCAL".equalsIgnoreCase(exposure)) {
            return demoActorId;
        }
        throw new AccessDeniedException("An authenticated user is required.");
    }
}
