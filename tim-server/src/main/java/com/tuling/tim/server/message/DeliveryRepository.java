package com.tuling.tim.server.message;

import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Durable ACK state; the JVM pending map is only a delivery acceleration cache. */
@Repository
public class DeliveryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public DeliveryRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void createPending(ChatMessage message) {
        jdbc.update("INSERT INTO message_delivery (message_id, recipient_id, status, attempt_count, next_retry_at, created_at, updated_at) VALUES (?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE message_id=message_id",
                message.getMessageId(), message.getToUserId());
    }

    public void markDelivering(String messageId, long recipientId, long nextRetryAt) {
        jdbc.update("UPDATE message_delivery SET status='DELIVERING', attempt_count=attempt_count+1, next_retry_at=FROM_UNIXTIME(? / 1000), updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status IN ('PENDING','DELIVERING')",
                nextRetryAt, messageId, recipientId);
    }

    public boolean acknowledge(String messageId, long recipientId) {
        return jdbc.update("UPDATE message_delivery SET status='ACKED', acked_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status IN ('DELIVERING','OFFLINE')",
                messageId, recipientId) == 1;
    }

    public void markOffline(String messageId, long recipientId) {
        jdbc.update("UPDATE message_delivery SET status='OFFLINE', updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status <> 'ACKED'",
                messageId, recipientId);
    }

    public List<DeliveryCandidate> claimDue(int limit, String owner, long leaseMs) {
        List<DeliveryCandidate> claimed = new java.util.ArrayList<>();
        List<DeliveryCandidate> candidates = jdbc.query(
                "SELECT d.message_id, d.recipient_id, m.body FROM message_delivery d JOIN im_message m ON m.message_id=d.message_id " +
                        "WHERE d.status IN ('PENDING','DELIVERING') AND d.next_retry_at <= CURRENT_TIMESTAMP " +
                        "AND (d.lease_until IS NULL OR d.lease_until <= CURRENT_TIMESTAMP) ORDER BY d.next_retry_at LIMIT ?",
                (rs, row) -> new DeliveryCandidate(rs.getString(1), rs.getLong(2), rs.getString(3)), limit);
        for (DeliveryCandidate candidate : candidates) {
            int updated = jdbc.update("UPDATE message_delivery SET lease_owner=?, lease_until=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 60 SECOND), updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status IN ('PENDING','DELIVERING') AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)",
                    owner, candidate.messageId(), candidate.recipientId());
            if (updated == 1) claimed.add(candidate);
        }
        return claimed;
    }

    public ChatMessage readMessage(DeliveryCandidate candidate) {
        try { return json.readValue(candidate.body(), ChatMessage.class); }
        catch (Exception e) { throw new IllegalStateException("deserialize durable delivery " + candidate.messageId(), e); }
    }

    public record DeliveryCandidate(String messageId, long recipientId, String body) { }
}
