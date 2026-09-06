package com.tuling.tim.server.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

/** MySQL/H2 history table. The unique message_id is the durable idempotency guard. */
@Repository
public class MessageHistoryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public MessageHistoryRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public boolean insertIfAbsent(ChatMessage message, String status) {
        try {
            jdbc.update("INSERT INTO im_message (message_id, client_message_id, from_user_id, to_user_id, group_id, body, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    message.getMessageId(), message.getClientMessageId(), message.getFromUserId(), message.getToUserId(), message.getGroupId(), json.writeValueAsString(message), status, message.getCreatedAt());
            return true;
        } catch (DuplicateKeyException e) { return false; }
        catch (JsonProcessingException e) { throw new IllegalStateException("serialize message", e); }
    }
    public void markRouting(String id) {
        jdbc.update("UPDATE im_message SET status='ROUTING' WHERE message_id=? AND status='PENDING'", id);
    }
    public void markDelivering(String id, long recipientId) {
        jdbc.update("UPDATE im_message SET status='DELIVERING' WHERE message_id=? AND to_user_id=? AND status IN ('PENDING','ROUTING','DELIVERING')", id, recipientId);
    }
    public boolean acknowledge(String id, long recipientId) {
        return jdbc.update("UPDATE im_message SET status='ACKED', acked_at=CURRENT_TIMESTAMP WHERE message_id=? AND to_user_id=? AND status IN ('DELIVERING','OFFLINE')", id, recipientId) == 1;
    }
    public void markOffline(String id) {
        jdbc.update("UPDATE im_message SET status='OFFLINE' WHERE message_id=? AND status IN ('PENDING','ROUTING','DELIVERING')", id);
    }
    public List<String> findAfter(long userId, long after, int limit) {
        return jdbc.queryForList("SELECT body FROM im_message WHERE to_user_id = ? AND created_at > ? ORDER BY created_at LIMIT ?", String.class, userId, after, limit);
    }
    @Transactional
    public long indexOffline(long userId, String messageId) {
        Long existing = findOfflineCursor(userId, messageId);
        if (existing != null) {
            markOffline(messageId);
            return existing;
        }
        jdbc.update("INSERT INTO offline_cursor_sequence (user_id, next_cursor) VALUES (?, 1) ON DUPLICATE KEY UPDATE user_id=user_id", userId);
        Long next = jdbc.queryForObject("SELECT next_cursor FROM offline_cursor_sequence WHERE user_id=? FOR UPDATE", Long.class, userId);
        long cursor = next == null ? 1L : next;
        try {
            jdbc.update("INSERT INTO offline_message_index (user_id, delivery_cursor, message_id) VALUES (?, ?, ?)", userId, cursor, messageId);
            jdbc.update("UPDATE offline_cursor_sequence SET next_cursor=? WHERE user_id=?", cursor + 1, userId);
            markOffline(messageId);
            return cursor;
        } catch (DuplicateKeyException duplicate) {
            Long winner = findOfflineCursor(userId, messageId);
            if (winner != null) {
                markOffline(messageId);
                return winner;
            }
            throw duplicate;
        }
    }
    public Long findOfflineCursor(long userId, String messageId) {
        List<Long> cursors = jdbc.queryForList("SELECT delivery_cursor FROM offline_message_index WHERE user_id=? AND message_id=?", Long.class, userId, messageId);
        return cursors.isEmpty() ? null : cursors.get(0);
    }
    public boolean acknowledgeOffline(long userId, long cursor) {
        Long maximum = jdbc.queryForObject("SELECT MAX(delivery_cursor) FROM offline_message_index WHERE user_id=?", Long.class, userId);
        if (maximum == null || cursor > maximum) return false;
        int updated = jdbc.update("UPDATE offline_user_cursor SET confirmed_cursor=CASE WHEN confirmed_cursor < ? THEN ? ELSE confirmed_cursor END, updated_at=CURRENT_TIMESTAMP WHERE user_id=?", cursor, cursor, userId);
        if (updated == 0) {
            try { jdbc.update("INSERT INTO offline_user_cursor (user_id, confirmed_cursor, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)", userId, cursor); }
            catch (DuplicateKeyException ignored) { }
        }
        return true;
    }
    public List<String> findBodies(Collection<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
        return jdbc.queryForList("SELECT body FROM im_message WHERE message_id IN (" + placeholders + ") ORDER BY created_at", String.class, messageIds.toArray());
    }
    public List<String> findOfflineAfter(long userId, long cursor, int limit) {
        return jdbc.queryForList("SELECT m.body FROM offline_message_index i JOIN im_message m ON m.message_id=i.message_id WHERE i.user_id=? AND i.delivery_cursor>? ORDER BY i.delivery_cursor LIMIT ?", String.class, userId, cursor, limit);
    }
    public List<OfflineMessage> findOfflineBodies(long userId, Collection<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
        List<Object> args = new ArrayList<>(); args.add(userId); args.addAll(messageIds);
        return jdbc.query("SELECT i.message_id, i.delivery_cursor, m.body FROM offline_message_index i JOIN im_message m ON m.message_id=i.message_id WHERE i.user_id=? AND i.message_id IN (" + placeholders + ") ORDER BY i.delivery_cursor",
                (rs, row) -> new OfflineMessage(rs.getString(1), rs.getLong(2), rs.getString(3)), args.toArray());
    }
    public List<OfflineMessage> findOfflineRecordsAfter(long userId, long cursor, int limit) {
        return jdbc.query("SELECT i.message_id, i.delivery_cursor, m.body FROM offline_message_index i JOIN im_message m ON m.message_id=i.message_id WHERE i.user_id=? AND i.delivery_cursor>? ORDER BY i.delivery_cursor LIMIT ?",
                (rs, row) -> new OfflineMessage(rs.getString(1), rs.getLong(2), rs.getString(3)), userId, cursor, limit);
    }
    public List<Long> findOfflineUsers(int limit) {
        return jdbc.queryForList("SELECT DISTINCT user_id FROM offline_message_index ORDER BY user_id LIMIT ?", Long.class, limit);
    }
    public List<OfflineMessage> findRecentOfflineRecords(long userId, int limit) {
        return jdbc.query("SELECT i.message_id, i.delivery_cursor, m.body FROM offline_message_index i JOIN im_message m ON m.message_id=i.message_id WHERE i.user_id=? ORDER BY i.delivery_cursor DESC LIMIT ?",
                (rs, row) -> new OfflineMessage(rs.getString(1), rs.getLong(2), rs.getString(3)), userId, limit);
    }
}
