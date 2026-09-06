package com.tuling.tim.server.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.message.ReliableMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.tuling.tim.server.mq.NodeMessageBus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.HashMap;
import java.util.Map;

/** Small groups write-diffuse; large groups append once and are read by member cursor. */
@Service
public class GroupMessageService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final NodeMessageBus bus;
    private final int writeFanoutLimit;
    public GroupMessageService(StringRedisTemplate redis, ReliableMessageService messages, ObjectMapper json, JdbcTemplate jdbc, NodeMessageBus bus,
                               @Value("${tim.group.write-fanout-limit:500}") int writeFanoutLimit) {
        this.redis = redis; this.json = json; this.jdbc = jdbc; this.bus = bus; this.writeFanoutLimit = writeFanoutLimit;
    }
    public void addMember(long groupId, long userId) {
        jdbc.update("INSERT INTO im_group (group_id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE group_id=group_id", groupId, "group-" + groupId);
        jdbc.update("INSERT INTO group_member (group_id, user_id, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE group_id=group_id", groupId, userId);
        redis.opsForSet().add(membersKey(groupId), String.valueOf(userId));
        NioSocketChannel channel = SessionSocketHolder.get(userId); if (channel != null) SessionSocketHolder.joinGroup(groupId, channel);
    }
    public void restoreLocalMembership(long userId, NioSocketChannel channel) {
        jdbc.queryForList("SELECT group_id FROM group_member WHERE user_id=?", Long.class, userId)
                .forEach(groupId -> SessionSocketHolder.joinGroup(groupId, channel));
    }
    public void removeMember(long groupId, long userId) {
        jdbc.update("DELETE FROM group_member WHERE group_id=? AND user_id=?", groupId, userId);
        redis.opsForSet().remove(membersKey(groupId), String.valueOf(userId));
        NioSocketChannel channel = SessionSocketHolder.get(userId);
        if (channel != null) SessionSocketHolder.leaveGroup(groupId, channel);
    }
    public String send(ChatMessage source) {
        Set<String> members = redis.opsForSet().members(membersKey(source.getGroupId()));
        if (members == null || !members.contains(String.valueOf(source.getFromUserId()))) throw new IllegalArgumentException("sender is not a group member");
        String messageId = source.getMessageId() == null ? String.valueOf(System.currentTimeMillis()) : source.getMessageId();
        source.setMessageId(messageId);
        long sequence = nextSequence(source.getGroupId());
        jdbc.update("INSERT INTO group_message (message_id, group_id, group_sequence, sender_id, content, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE message_id=message_id", messageId, source.getGroupId(), sequence, source.getFromUserId(), source.getContent());
        if (members.size() < writeFanoutLimit) {
            for (String member : members) jdbc.update("INSERT INTO group_message_inbox (group_id, user_id, message_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE message_id=message_id", source.getGroupId(), Long.parseLong(member), messageId);
            bus.broadcastGroup(source);
            return "WRITE_FANOUT";
        }
        try {
            redis.opsForZSet().add("im:group:messages:" + source.getGroupId(), messageId, sequence);
            bus.broadcastGroup(source);
            return "READ_FANOUT";
        } catch (Exception e) { throw new IllegalStateException("store group message", e); }
    }
    public List<String> pull(long groupId, long userId, long cursor, int limit) {
        if (!Boolean.TRUE.equals(redis.opsForSet().isMember(membersKey(groupId), String.valueOf(userId)))) throw new IllegalArgumentException("user is not a group member");
        List<String> ids = new ArrayList<>(redis.opsForZSet().rangeByScore("im:group:messages:" + groupId, cursor + 1, Double.MAX_VALUE, 0, limit));
        if (ids.isEmpty()) {
            Long memberCount = redis.opsForSet().size(membersKey(groupId));
            if (memberCount != null && memberCount >= writeFanoutLimit) {
                // Large groups have no per-member inbox rows. MySQL remains
                // the durable source when the Redis read index is incomplete.
                ids = jdbc.queryForList("SELECT message_id FROM group_message WHERE group_id=? AND group_sequence>? ORDER BY group_sequence LIMIT ?", String.class, groupId, cursor, limit);
            } else {
                ids = jdbc.queryForList("SELECT gm.message_id FROM group_message_inbox i JOIN group_message gm ON gm.message_id=i.message_id WHERE i.group_id=? AND i.user_id=? AND gm.group_sequence>? ORDER BY gm.group_sequence LIMIT ?", String.class, groupId, userId, cursor, limit);
            }
        }
        List<String> bodies = loadBodies(ids, groupId);
        return bodies;
    }
    public void acknowledge(long groupId, long userId, long cursor) {
        if (cursor <= 0) return;
        jdbc.update("INSERT INTO group_member_cursor (group_id, user_id, last_read_sequence) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE last_read_sequence=GREATEST(last_read_sequence, VALUES(last_read_sequence))",
                groupId, userId, cursor);
    }
    private synchronized long nextSequence(long groupId) {
        jdbc.update("INSERT INTO group_sequence (group_id, next_sequence) VALUES (?, 1) ON DUPLICATE KEY UPDATE group_id=group_id", groupId);
        Long value = jdbc.queryForObject("SELECT next_sequence FROM group_sequence WHERE group_id=?", Long.class, groupId);
        long sequence = value == null ? 1L : value;
        jdbc.update("UPDATE group_sequence SET next_sequence=? WHERE group_id=?", sequence + 1, groupId);
        return sequence;
    }
    private List<String> loadBodies(List<String> ids, long groupId) {
        List<String> bodies = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> row = jdbc.queryForMap("SELECT sender_id, content, group_sequence, created_at FROM group_message WHERE group_id=? AND message_id=?", groupId, id);
            try {
                Map<String, Object> body = new HashMap<>(); body.put("messageId", id); body.put("groupId", groupId);
                body.put("fromUserId", row.get("sender_id")); body.put("content", row.get("content"));
                body.put("groupSequence", row.get("group_sequence")); body.put("createdAt", row.get("created_at"));
                bodies.add(json.writeValueAsString(body));
            } catch (Exception e) { throw new IllegalStateException("serialize group message", e); }
        }
        return bodies;
    }
    private String membersKey(long id) { return "im:group:members:" + id; }
}
