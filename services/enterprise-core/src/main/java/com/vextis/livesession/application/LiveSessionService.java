package com.vextis.livesession.application;

import com.vextis.livesession.application.port.LiveSessionRepository;
import com.vextis.livesession.domain.LiveSession;
import com.vextis.livesession.domain.LiveSessionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LiveSessionService implements CreateLiveSessionUseCase, CloseLiveSessionUseCase, ValidateLiveSessionUseCase {

    static final Duration SESSION_TTL = Duration.ofMinutes(5);

    private final LiveSessionRepository repository;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final String websocketBaseUrl;
    private final int maxSessionsPerActor;
    private final Duration quotaWindow;

    public LiveSessionService(
            LiveSessionRepository repository,
            Clock clock,
            @Value("${vextis.agent-runtime.public-websocket-base-url:}") String websocketBaseUrl,
            @Value("${vextis.live.max-sessions-per-actor:5}") int maxSessionsPerActor,
            @Value("${vextis.live.session-quota-window:PT1H}") Duration quotaWindow
    ) {
        this.repository = repository;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
        this.websocketBaseUrl = websocketBaseUrl;
        this.maxSessionsPerActor = maxSessionsPerActor;
        this.quotaWindow = quotaWindow;
    }

    @Override
    public LiveSessionCredential create(CreateLiveSessionCommand command) {
        if (websocketBaseUrl.isBlank()) {
            throw new IllegalStateException("Live voice is not configured on this deployment");
        }
        Instant now = clock.instant();
        enforceActorQuota(command.tenantId(), command.actorId(), now);
        Instant expiresAt = now.plus(SESSION_TTL);
        UUID id = UUID.randomUUID();
        String token = generateToken();

        LiveSession session = new LiveSession(
                id, command.tenantId(), command.conversationId(), command.actorId(), LiveSessionState.CREATED,
                now, expiresAt);
        repository.create(session, hash(token));

        String websocketUrl = websocketBaseUrl + "/v1/live/" + id;
        return new LiveSessionCredential(id, websocketUrl, token, expiresAt);
    }

    @Override
    public boolean close(String tenantId, UUID sessionId) {
        return repository.close(tenantId, sessionId, clock.instant());
    }

    @Override
    public LiveSessionValidation validate(UUID sessionId, String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return LiveSessionValidation.invalid();
        }
        return repository.claim(sessionId, hash(presentedToken), clock.instant());
    }

    /**
     * Caps how many sessions one actor may create inside the rolling window.
     * Enterprise Core is the only place this can be enforced: the gateway sees a
     * session token, not who asked for it.
     */
    private void enforceActorQuota(String tenantId, String actorId, Instant now) {
        if (maxSessionsPerActor <= 0) {
            return;
        }
        int recent = repository.countCreatedSince(tenantId, actorId, now.minus(quotaWindow));
        if (recent >= maxSessionsPerActor) {
            throw new LiveSessionQuotaExceededException(maxSessionsPerActor);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
