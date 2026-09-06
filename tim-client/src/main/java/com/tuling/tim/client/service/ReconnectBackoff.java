package com.tuling.tim.client.service;

/** Exponential reconnect delay policy shared by the client scheduler and tests. */
public final class ReconnectBackoff {
    private ReconnectBackoff() { }

    public static long baseDelaySeconds(int attempt) {
        if (attempt < 0) throw new IllegalArgumentException("attempt must be non-negative");
        return Math.min(30L, 1L << Math.min(attempt, 5));
    }

    public static long jitterUpperExclusive(long baseDelaySeconds) {
        if (baseDelaySeconds <= 0) throw new IllegalArgumentException("base delay must be positive");
        return Math.max(1L, baseDelaySeconds / 4 + 1);
    }
}
