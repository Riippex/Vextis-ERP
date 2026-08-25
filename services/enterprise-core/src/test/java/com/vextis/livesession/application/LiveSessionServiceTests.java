package com.vextis.livesession.application;

import com.vextis.livesession.application.port.LiveSessionRepository;
import com.vextis.livesession.domain.LiveSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveSessionServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TENANT_ID = "demo-tenant";

    @Test
    void createsAShortLivedSessionWithAWorkingCredential() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = new LiveSessionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), "wss://agent-runtime.example.com");

        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(credential.websocketUrl()).isEqualTo("wss://agent-runtime.example.com/v1/live/" + credential.id());
        assertThat(credential.expiresAt()).isEqualTo(NOW.plus(LiveSessionService.SESSION_TTL));

        LiveSessionValidation validation = service.validate(credential.id(), credential.sessionToken());
        assertThat(validation.valid()).isTrue();
        assertThat(validation.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void rejectsAnUnconfiguredDeployment() {
        LiveSessionService service = new LiveSessionService(new InMemoryRepository(), Clock.fixed(NOW, ZoneOffset.UTC), "");

        assertThatThrownBy(() -> service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTokenCanOnlyBeClaimedOnce() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = new LiveSessionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), "wss://agent-runtime.example.com");
        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(service.validate(credential.id(), credential.sessionToken()).valid()).isTrue();
        assertThat(service.validate(credential.id(), credential.sessionToken()).valid()).isFalse();
    }

    @Test
    void rejectsTheWrongToken() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = new LiveSessionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), "wss://agent-runtime.example.com");
        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(service.validate(credential.id(), "not-the-right-token").valid()).isFalse();
    }

    @Test
    void closesAnOwnedSession() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = new LiveSessionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), "wss://agent-runtime.example.com");
        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(service.close(TENANT_ID, credential.id())).isTrue();
        assertThat(service.validate(credential.id(), credential.sessionToken()).valid()).isFalse();
    }

    private static final class InMemoryRepository implements LiveSessionRepository {
        private final Map<UUID, LiveSession> sessions = new HashMap<>();
        private final Map<UUID, String> tokenHashes = new HashMap<>();

        @Override
        public void create(LiveSession session, String tokenHash) {
            sessions.put(session.id(), session);
            tokenHashes.put(session.id(), tokenHash);
        }

        @Override
        public LiveSessionValidation claim(UUID sessionId, String presentedTokenHash, Instant now) {
            LiveSession session = sessions.get(sessionId);
            if (session == null || session.state() != com.vextis.livesession.domain.LiveSessionState.CREATED) {
                return LiveSessionValidation.invalid();
            }
            if (!tokenHashes.get(sessionId).equals(presentedTokenHash)) {
                return LiveSessionValidation.invalid();
            }
            if (!now.isBefore(session.expiresAt())) {
                return LiveSessionValidation.invalid();
            }
            sessions.put(sessionId, new LiveSession(
                    session.id(), session.tenantId(), session.conversationId(), session.actorId(),
                    com.vextis.livesession.domain.LiveSessionState.ACTIVE, session.createdAt(), session.expiresAt()));
            return new LiveSessionValidation(true, session.tenantId(), session.conversationId(), session.expiresAt());
        }

        @Override
        public boolean close(String tenantId, UUID sessionId, Instant closedAt) {
            LiveSession session = sessions.get(sessionId);
            if (session == null || !session.tenantId().equals(tenantId)
                    || session.state() == com.vextis.livesession.domain.LiveSessionState.CLOSED) {
                return false;
            }
            sessions.put(sessionId, new LiveSession(
                    session.id(), session.tenantId(), session.conversationId(), session.actorId(),
                    com.vextis.livesession.domain.LiveSessionState.CLOSED, session.createdAt(), session.expiresAt()));
            return true;
        }
    }
}
