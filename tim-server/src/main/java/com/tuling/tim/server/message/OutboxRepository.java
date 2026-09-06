package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Small transactional-outbox store. The database commit is the durable hand-off point. */
@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public OutboxRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void append(ChatMessage message) {
        append(message, "PRIVATE_MESSAGE_CREATED");
    }
    public void appendGroup(ChatMessage message) {
        append(message, "GROUP_MESSAGE_CREATED");
    }
    private void append(ChatMessage message, String eventType) {
        try {
            jdbc.update("INSERT INTO outbox_event (event_id, event_type, message_id, payload, status, retry_count, next_retry_at, created_at) VALUES (?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE event_id = event_id",
                    message.getMessageId(), eventType, message.getMessageId(), json.writeValueAsString(message));
        } catch (Exception e) { throw new IllegalStateException("append outbox event", e); }
    }

    /** Claims due events with a short lease so two relay instances do not send the same row concurrently. */
    public List<OutboxEvent> claimPending(int limit) {
        List<OutboxEvent> claimed = new java.util.ArrayList<>();
        List<OutboxEvent> candidates = jdbc.query(
                "SELECT event_id, event_type, message_id, payload FROM outbox_event WHERE status IN ('PENDING','PROCESSING') AND next_retry_at <= CURRENT_TIMESTAMP ORDER BY created_at LIMIT ?",
                (rs, row) -> new OutboxEvent(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), limit);
        for (OutboxEvent candidate : candidates) {
            int updated = jdbc.update("UPDATE outbox_event SET status='PROCESSING', next_retry_at=? WHERE event_id=? AND status IN ('PENDING','PROCESSING') AND next_retry_at <= CURRENT_TIMESTAMP",
                    Timestamp.from(Instant.now().plusSeconds(60)), candidate.eventId());
            if (updated == 1) claimed.add(candidate);
        }
        return claimed;
    }
    public void sent(String eventId) { jdbc.update("UPDATE outbox_event SET status='SENT', sent_at=CURRENT_TIMESTAMP WHERE event_id=? AND status='PROCESSING'", eventId); }
    public void retry(String eventId, String error, int maxRetries) {
        jdbc.update("UPDATE outbox_event SET status=CASE WHEN retry_count + 1 >= ? THEN 'DEAD' ELSE 'PENDING' END, retry_count=retry_count+1, last_error=?, next_retry_at=? WHERE event_id=? AND status='PROCESSING'",
                maxRetries, error, Timestamp.from(Instant.now().plusSeconds(5)), eventId);
    }
    public long pendingCount() { return count("status IN ('PENDING','PROCESSING')"); }
    public long deadCount() { return count("status='DEAD'"); }
    private long count(String predicate) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE " + predicate, Long.class);
        return value == null ? 0L : value;
    }
    public record OutboxEvent(String eventId, String eventType, String messageId, String payload) {}
}
