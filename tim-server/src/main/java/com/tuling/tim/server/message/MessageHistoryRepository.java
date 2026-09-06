package com.tuling.tim.server.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

/** MySQL/H2 history table. The unique message_id is the durable idempotency guard. */
@Repository
public class MessageHistoryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public MessageHistoryRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public boolean insertIfAbsent(ChatMessage message, String status) {
        try {
            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM im_message WHERE message_id = ? OR (from_user_id = ? AND client_message_id = ?)", Integer.class,
                    message.getMessageId(), message.getFromUserId(), message.getClientMessageId());
            if (existing != null && existing > 0) return false;
            int updated = jdbc.update("INSERT INTO im_message (message_id, client_message_id, from_user_id, to_user_id, group_id, body, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE message_id = message_id",
                    message.getMessageId(), message.getClientMessageId(), message.getFromUserId(), message.getToUserId(), message.getGroupId(), json.writeValueAsString(message), status, message.getCreatedAt());
            return updated > 0;
        } catch (DuplicateKeyException e) { return false; }
        catch (JsonProcessingException e) { throw new IllegalStateException("serialize message", e); }
    }
    public void updateStatus(String id, String status) { jdbc.update("UPDATE im_message SET status = ? WHERE message_id = ?", status, id); }
    public List<String> findAfter(long userId, long after, int limit) {
        return jdbc.queryForList("SELECT body FROM im_message WHERE to_user_id = ? AND created_at > ? ORDER BY created_at LIMIT ?", String.class, userId, after, limit);
    }
    public void indexOffline(long userId, long cursor, String messageId) {
        jdbc.update("INSERT INTO offline_message_index (user_id, delivery_cursor, message_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE message_id=message_id", userId, cursor, messageId);
    }
    public List<String> findBodies(Collection<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
        return jdbc.queryForList("SELECT body FROM im_message WHERE message_id IN (" + placeholders + ") ORDER BY created_at", String.class, messageIds.toArray());
    }
    public List<String> findOfflineAfter(long userId, long cursor, int limit) {
        return jdbc.queryForList("SELECT m.body FROM offline_message_index i JOIN im_message m ON m.message_id=i.message_id WHERE i.user_id=? AND i.delivery_cursor>? ORDER BY i.delivery_cursor LIMIT ?", String.class, userId, cursor, limit);
    }
}
