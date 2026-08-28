-- Every createLiveSession call now runs countCreatedSince(tenant_id, actor_id,
-- since) to enforce the per-actor quota (see LiveSessionService). Without this
-- index that query is a sequential scan over the whole table, and it runs
-- inside the same transaction as the pg_advisory_xact_lock that serializes
-- concurrent creates for one actor, so a slow count directly extends how long
-- other requests from that actor sit blocked on the lock.

CREATE INDEX ix_live_sessions_actor_quota
    ON live_sessions (tenant_id, actor_id, created_at);
