package com.vextis.livesession.api.internal;

import com.vextis.livesession.application.LiveSessionValidation;
import com.vextis.livesession.application.ValidateLiveSessionUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/live-sessions")
class LiveSessionValidationController {

    private final ValidateLiveSessionUseCase validateLiveSession;
    private final LiveSessionToolAuthorizer authorizer;

    LiveSessionValidationController(
            ValidateLiveSessionUseCase validateLiveSession,
            LiveSessionToolAuthorizer authorizer
    ) {
        this.validateLiveSession = validateLiveSession;
        this.authorizer = authorizer;
    }

    @PostMapping("/{sessionId}/validate")
    LiveSessionValidationResponse validate(
            @PathVariable UUID sessionId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader("X-Live-Session-Token") @NotBlank @Size(min = 16) String sessionToken
    ) {
        authorizer.authorize(authorization, agentId);
        LiveSessionValidation validation = validateLiveSession.validate(sessionId, sessionToken);
        return LiveSessionValidationResponse.from(validation);
    }

    record LiveSessionValidationResponse(boolean valid, String tenantId, UUID conversationId, String expiresAt) {
        static LiveSessionValidationResponse from(LiveSessionValidation validation) {
            return new LiveSessionValidationResponse(
                    validation.valid(),
                    validation.tenantId(),
                    validation.conversationId(),
                    validation.expiresAt() == null ? null : validation.expiresAt().toString());
        }
    }
}
