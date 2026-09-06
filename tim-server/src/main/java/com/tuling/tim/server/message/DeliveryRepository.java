package com.tuling.tim.server.message;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Durable ACK state; the JVM pending map is only a delivery acceleration cache. */
@Repository
public class DeliveryRepository {
    private final JdbcTemplate jdbc;
    public DeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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

    public List<String> recoverDueMessageIds(int limit) {
        return jdbc.queryForList("SELECT message_id FROM message_delivery WHERE status IN ('PENDING','DELIVERING') AND next_retry_at <= CURRENT_TIMESTAMP ORDER BY next_retry_at LIMIT ?", String.class, limit);
    }
}
