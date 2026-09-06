package com.tuling.tim.server.message;

/** A server-owned delivery awaiting the receiving client's ACK. */
public class PendingDelivery {
    private final ChatMessage message;
    private int attempts;
    private long nextRetryAt;

    public PendingDelivery(ChatMessage message, long nextRetryAt) {
        this.message = message;
        this.nextRetryAt = nextRetryAt;
    }
    public ChatMessage getMessage() { return message; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts(long nextRetryAt) { attempts++; this.nextRetryAt = nextRetryAt; }
    public long getNextRetryAt() { return nextRetryAt; }
}
