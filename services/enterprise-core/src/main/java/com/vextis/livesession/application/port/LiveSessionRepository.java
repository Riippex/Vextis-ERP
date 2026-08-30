package com.vextis.livesession.application.port;

import com.vextis.livesession.application.LiveSessionValidation;
import com.vextis.livesession.domain.LiveSession;

import java.time.Instant;
import java.util.UUID;

public interface LiveSessionRepository {

    void create(LiveSession session, String tokenHash);

    /**
     * Serializes concurrent {@code create} calls for one actor within the
     * current transaction. Postgres advisory locks are transaction-scoped, so
     * this is released automatically at commit or rollback and requires no
     * matching "unlock" call.
     *
     * <p>Without it, {@link #countCreatedSince} followed by an insert is a
     * check-then-act race: two concurrent requests for the same actor can each
     * read the same pre-insert count, both see it under the limit, and both
     * insert, so the quota is exceeded. This lock forces the second caller to
     * wait until the first has committed its insert (or rolled back), so the
     * count it then reads is never stale.
     */
    void acquireActorQuotaLock(String tenantId, String actorId);

    /**
     * Sessions this actor created for this tenant at or after {@code since},
     * whatever state they reached. Counting creations rather than live sockets
     * is deliberate: opening and abandoning sessions in a loop is the cheap way
     * to burn model minutes, and a closed session has already cost them.
     */
    int countCreatedSince(String tenantId, String actorId, Instant since);

    /**
     * Atomically checks the presented token's hash, the session's tenant-free
     * existence, its CREATED state, and its expiry, then transitions it to
     * ACTIVE — all in one conditional update, so this can only ever succeed
     * once for a given session.
     */
    LiveSessionValidation claim(UUID sessionId, String presentedTokenHash, Instant now);

    /** Closes a CREATED or ACTIVE session owned by this tenant. Returns false if nothing matched. */
    boolean close(String tenantId, UUID sessionId, Instant closedAt);
}
