package com.tuling.tim.server.message;

import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Durable ACK state; the JVM pending map is only a delivery acceleration cache. */
@Repository
public class DeliveryRepository {
    private static final String PENDING = DeliveryStatus.PENDING.name();
    private static final String DELIVERING = DeliveryStatus.DELIVERING.name();
    private static final String OFFLINE = DeliveryStatus.OFFLINE.name();
    private static final String ACKED = DeliveryStatus.ACKED.name();
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public DeliveryRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void createPending(ChatMessage message) {
        jdbc.update("INSERT INTO message_delivery (message_id, recipient_id, status, attempt_count, next_retry_at, created_at, updated_at) VALUES (?, ?, '" + PENDING + "', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE message_id=message_id",
                message.getMessageId(), message.getToUserId());
    }

    public void markDelivering(String messageId, long recipientId, long nextRetryAt) {
        jdbc.update("UPDATE message_delivery SET status='" + DELIVERING + "', attempt_count=attempt_count+1, next_retry_at=?, updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status IN ('" + PENDING + "','" + DELIVERING + "')",
                Timestamp.from(Instant.ofEpochMilli(nextRetryAt)), messageId, recipientId);
    }

    public boolean acknowledge(String messageId, long recipientId) {
        return jdbc.update("UPDATE message_delivery SET status='" + ACKED + "', acked_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status IN ('" + DELIVERING + "','" + OFFLINE + "')",
                messageId, recipientId) == 1;
    }

    public void markOffline(String messageId, long recipientId) {
        jdbc.update("UPDATE message_delivery SET status='" + OFFLINE + "', updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status <> '" + ACKED + "'",
                messageId, recipientId);
    }

    public List<DeliveryCandidate> claimDue(int limit, String owner, long leaseMs) {
        List<DeliveryCandidate> claimed = new java.util.ArrayList<>();
        List<DeliveryCandidate> candidates = jdbc.query(
                "SELECT d.message_id, d.recipient_id, m.body, d.attempt_count FROM message_delivery d JOIN im_message m ON m.message_id=d.message_id " +
                        "WHERE d.status IN ('" + PENDING + "','" + DELIVERING + "') AND d.next_retry_at <= CURRENT_TIMESTAMP " +
                        "AND (d.lease_until IS NULL OR d.lease_until <= CURRENT_TIMESTAMP) ORDER BY d.next_retry_at LIMIT ?",
                (rs, row) -> new DeliveryCandidate(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), limit);
        for (DeliveryCandidate candidate : candidates) {
            Timestamp leaseUntil = Timestamp.from(Instant.now().plusMillis(Math.max(1L, leaseMs)));
            int updated = jdbc.update("UPDATE message_delivery SET lease_owner=?, lease_until=?, updated_at=CURRENT_TIMESTAMP WHERE message_id=? AND recipient_id=? AND status IN ('" + PENDING + "','" + DELIVERING + "') AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)",
                    owner, leaseUntil, candidate.messageId(), candidate.recipientId());
            if (updated == 1) claimed.add(candidate);
        }
        return claimed;
    }

    public long pendingCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM message_delivery WHERE status IN ('" + PENDING + "','" + DELIVERING + "')", Long.class);
        return count == null ? 0L : count;
    }

    public double oldestPendingSeconds() {
        Timestamp oldest = jdbc.queryForObject("SELECT MIN(created_at) FROM message_delivery WHERE status IN ('" + PENDING + "','" + DELIVERING + "')", Timestamp.class);
        return oldest == null ? 0D : Math.max(0D, (System.currentTimeMillis() - oldest.getTime()) / 1000D);
    }

    public ChatMessage readMessage(DeliveryCandidate candidate) {
        try { return json.readValue(candidate.body(), ChatMessage.class); }
        catch (Exception e) { throw new IllegalStateException("deserialize durable delivery " + candidate.messageId(), e); }
    }

    public record DeliveryCandidate(String messageId, long recipientId, String body, int attemptCount) { }
}
