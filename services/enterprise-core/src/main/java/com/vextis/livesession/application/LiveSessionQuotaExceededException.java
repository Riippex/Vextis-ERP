package com.vextis.livesession.application;

/**
 * One actor asked for more Live sessions than their quota allows.
 *
 * <p>createLiveSession is the only thing standing between a signed-in user and
 * Vertex AI Gemini Live minutes, and the socket it authorizes holds a Cloud Run
 * instance for the whole session. Without a per-actor bound, one account could
 * mint sessions in a loop and spend the model budget.
 */
public class LiveSessionQuotaExceededException extends RuntimeException {

    private final int limit;

    public LiveSessionQuotaExceededException(int limit) {
        super("Live session quota reached: at most " + limit
                + " sessions may be created per actor within the configured window");
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
