package com.tuling.tim.server.util;

/** Immutable identity bound to one authenticated TCP connection. */
public final class ConnectionSession {
    private final long userId;
    private final String sessionId;
    private final long epoch;

    public ConnectionSession(long userId, String sessionId, long epoch) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.epoch = epoch;
    }
    public long getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public long getEpoch() { return epoch; }
}
