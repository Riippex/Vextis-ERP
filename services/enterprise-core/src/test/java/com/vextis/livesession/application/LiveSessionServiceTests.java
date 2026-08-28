package com.vextis.livesession.application;

import com.vextis.livesession.application.port.LiveSessionRepository;
import com.vextis.livesession.domain.LiveSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
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
    private static final int DEFAULT_QUOTA = 5;

    @Test
    void createsAShortLivedSessionWithAWorkingCredential() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, "wss://agent-runtime.example.com");

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
        LiveSessionService service = newService(new InMemoryRepository(), "");

        assertThatThrownBy(() -> service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTokenCanOnlyBeClaimedOnce() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, "wss://agent-runtime.example.com");
        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(service.validate(credential.id(), credential.sessionToken()).valid()).isTrue();
        assertThat(service.validate(credential.id(), credential.sessionToken()).valid()).isFalse();
    }

    @Test
    void rejectsTheWrongToken() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, "wss://agent-runtime.example.com");
        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(service.validate(credential.id(), "not-the-right-token").valid()).isFalse();
    }

    @Test
    void closesAnOwnedSession() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, "wss://agent-runtime.example.com");
        LiveSessionCredential credential = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThat(service.close(TENANT_ID, credential.id())).isTrue();
        assertThat(service.validate(credential.id(), credential.sessionToken()).valid()).isFalse();
    }

    @Test
    void refusesAnActorPastTheirSessionQuota() {
        // createLiveSession is the only gate in front of Vertex AI Live minutes,
        // and each session pins a Cloud Run instance for its whole duration.
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, 2, Duration.ofHours(1));

        service.create(new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));
        service.create(new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID())))
                .isInstanceOfSatisfying(LiveSessionQuotaExceededException.class,
                        exception -> assertThat(exception.limit()).isEqualTo(2));
    }

    @Test
    void closingASessionDoesNotRefundTheQuota() {
        // Otherwise open-and-close in a loop costs nothing and the cap is
        // decorative; the minutes have already been paid for.
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, 1, Duration.ofHours(1));

        LiveSessionCredential first = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));
        assertThat(service.close(TENANT_ID, first.id())).isTrue();

        assertThatThrownBy(() -> service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID())))
                .isInstanceOf(LiveSessionQuotaExceededException.class);
    }

    @Test
    void theQuotaIsPerActor() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, 1, Duration.ofHours(1));

        service.create(new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));

        // A different person is not blocked by someone else's usage.
        LiveSessionCredential other = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-456", UUID.randomUUID()));
        assertThat(other.sessionToken()).isNotBlank();
    }

    @Test
    void sessionsOlderThanTheWindowNoLongerCount() {
        InMemoryRepository repository = new InMemoryRepository();
        MutableClock clock = new MutableClock(NOW);
        LiveSessionService service = new LiveSessionService(
                repository, clock, "wss://agent-runtime.example.com", 1, Duration.ofHours(1));

        service.create(new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));
        assertThatThrownBy(() -> service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID())))
                .isInstanceOf(LiveSessionQuotaExceededException.class);

        // The cap is a rolling window, not a lifetime allowance.
        clock.advance(Duration.ofHours(2));

        LiveSessionCredential later = service.create(
                new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));
        assertThat(later.sessionToken()).isNotBlank();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void aZeroQuotaDisablesTheCap() {
        InMemoryRepository repository = new InMemoryRepository();
        LiveSessionService service = newService(repository, 0, Duration.ofHours(1));

        for (int i = 0; i < 10; i++) {
            service.create(new CreateLiveSessionCommand(TENANT_ID, "firebase-user-123", UUID.randomUUID()));
        }

        assertThat(repository.countCreatedSince(TENANT_ID, "firebase-user-123", NOW)).isEqualTo(10);
    }

    private static LiveSessionService newService(LiveSessionRepository repository, String websocketBaseUrl) {
        return new LiveSessionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), websocketBaseUrl,
                DEFAULT_QUOTA, Duration.ofHours(1));
    }

    private static LiveSessionService newService(
            LiveSessionRepository repository, int maxSessionsPerActor, Duration window) {
        return new LiveSessionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), "wss://agent-runtime.example.com",
                maxSessionsPerActor, window);
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
        public void acquireActorQuotaLock(String tenantId, String actorId) {
            // No-op: these tests are single-threaded, so there is no concurrent
            // caller to serialize against. The real lock is only meaningful
            // against a real Postgres connection under concurrent load — see
            // LiveSessionServiceConcurrencyTests for that proof.
        }

        @Override
        public int countCreatedSince(String tenantId, String actorId, Instant since) {
            return (int) sessions.values().stream()
                    .filter(session -> session.tenantId().equals(tenantId))
                    .filter(session -> session.actorId().equals(actorId))
                    .filter(session -> !session.createdAt().isBefore(since))
                    .count();
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
