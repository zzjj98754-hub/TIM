package com.tuling.tim.server.message;

/** Durable offline body plus the per-user cursor required for explicit acknowledgement. */
public class OfflineMessage {
    private final String messageId;
    private final long deliveryCursor;
    private final String body;

    public OfflineMessage(String messageId, long deliveryCursor, String body) {
        this.messageId = messageId;
        this.deliveryCursor = deliveryCursor;
        this.body = body;
    }
    public String getMessageId() { return messageId; }
    public long getDeliveryCursor() { return deliveryCursor; }
    public String getBody() { return body; }
}
