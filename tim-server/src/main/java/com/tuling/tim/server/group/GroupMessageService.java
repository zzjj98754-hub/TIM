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

/** Small groups write-diffuse; large groups append once and are read by member cursor. */
@Service
public class GroupMessageService {
    private final StringRedisTemplate redis;
    private final ReliableMessageService messages;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final NodeMessageBus bus;
    private final int writeFanoutLimit;
    public GroupMessageService(StringRedisTemplate redis, ReliableMessageService messages, ObjectMapper json, JdbcTemplate jdbc, NodeMessageBus bus,
                               @Value("${tim.group.write-fanout-limit:500}") int writeFanoutLimit) {
        this.redis = redis; this.messages = messages; this.json = json; this.jdbc = jdbc; this.bus = bus; this.writeFanoutLimit = writeFanoutLimit;
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
    public String send(ChatMessage source) {
        Set<String> members = redis.opsForSet().members(membersKey(source.getGroupId()));
        if (members == null || !members.contains(String.valueOf(source.getFromUserId()))) throw new IllegalArgumentException("sender is not a group member");
        String messageId = source.getMessageId() == null ? String.valueOf(System.currentTimeMillis()) : source.getMessageId();
        source.setMessageId(messageId);
        jdbc.update("INSERT INTO group_message (message_id, group_id, sender_id, content, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE message_id=message_id", messageId, source.getGroupId(), source.getFromUserId(), source.getContent());
        if (members.size() < writeFanoutLimit) {
            for (String member : members) jdbc.update("INSERT INTO group_message_inbox (group_id, user_id, message_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE message_id=message_id", source.getGroupId(), Long.parseLong(member), messageId);
            bus.broadcastGroup(source);
            return "WRITE_FANOUT";
        }
        try {
            redis.opsForZSet().add("im:group:messages:" + source.getGroupId(), messageId, source.getCreatedAt());
            bus.broadcastGroup(source);
            return "READ_FANOUT";
        } catch (Exception e) { throw new IllegalStateException("store group message", e); }
    }
    public List<String> pull(long groupId, long userId, long cursor, int limit) {
        List<String> values = new ArrayList<>(redis.opsForZSet().rangeByScore("im:group:messages:" + groupId, cursor + 1, Double.MAX_VALUE, 0, limit));
        if (!Boolean.TRUE.equals(redis.opsForSet().isMember(membersKey(groupId), String.valueOf(userId)))) throw new IllegalArgumentException("user is not a group member");
        if (values.isEmpty()) values = jdbc.queryForList("SELECT message_id FROM group_message_inbox WHERE group_id=? AND user_id=? ORDER BY created_at LIMIT ?", String.class, groupId, userId, limit);
        if (!values.isEmpty()) redis.opsForValue().set("im:group:cursor:" + groupId + ":" + userId, values.get(values.size() - 1));
        return values;
    }
    private String membersKey(long id) { return "im:group:members:" + id; }
    private ChatMessage copy(ChatMessage source) {
        ChatMessage copy = new ChatMessage(); copy.setFromUserId(source.getFromUserId()); copy.setGroupId(source.getGroupId());
        copy.setContent(source.getContent()); copy.setCreatedAt(source.getCreatedAt()); return copy;
    }
}
