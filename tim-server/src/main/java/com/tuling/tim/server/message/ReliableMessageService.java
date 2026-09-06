package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.server.mq.NodeMessageBus;
import com.tuling.tim.server.route.RedisRouteService;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Main learning path: route -> node transport -> local push -> ACK -> bounded retry/offline. */
@Service
public class ReliableMessageService {
    private final RedisRouteService routes;
    private final StringRedisTemplate redis;
    private final MessageHistoryRepository history;
    private final ObjectMapper json;
    private final SnowflakeIdGenerator ids;
    private final OutboxRepository outbox;
    private final Map<String, PendingDelivery> pending = new ConcurrentHashMap<>();
    private NodeMessageBus bus;
    private final int maxRetries;
    private final long retryMs;
    private final int offlineLimit;

    public ReliableMessageService(RedisRouteService routes, StringRedisTemplate redis, MessageHistoryRepository history,
                                  ObjectMapper json, SnowflakeIdGenerator ids, OutboxRepository outbox,
                                  @Value("${tim.delivery.max-retries:3}") int maxRetries,
                                  @Value("${tim.delivery.retry-ms:5000}") long retryMs,
                                  @Value("${tim.offline.max-size:1000}") int offlineLimit) {
        this.routes = routes; this.redis = redis; this.history = history; this.json = json; this.ids = ids; this.outbox = outbox;
        this.maxRetries = maxRetries; this.retryMs = retryMs; this.offlineLimit = offlineLimit;
    }
    @org.springframework.beans.factory.annotation.Autowired
    void setBus(NodeMessageBus bus) { this.bus = bus; }

    @Transactional
    public void accept(ChatMessage message) {
        if (message.getMessageId() == null || message.getMessageId().isEmpty()) message.setMessageId(ids.nextId());
        if (message.getCreatedAt() == 0) message.setCreatedAt(System.currentTimeMillis());
        if (message.getClientMessageId() == null || message.getClientMessageId().isBlank()) message.setClientMessageId(message.getMessageId());
        boolean inserted = history.insertIfAbsent(message, "PENDING");
        if (!inserted) return;
        redis.opsForValue().setIfAbsent("tim:dedup:message:" + message.getMessageId(), "1", Duration.ofHours(24));
        outbox.append(message);
    }

    public void receiveFromNode(ChatMessage message) { deliverLocalOrOffline(message); }
    private void dispatch(ChatMessage message) {
        String target = routes.findServer(message.getToUserId());
        if (target == null) { saveOffline(message, "recipient offline"); return; }
        if (routes.serverId().equals(target)) deliverLocalOrOffline(message); else bus.forward(target, message);
    }
    private void deliverLocalOrOffline(ChatMessage message) {
        Channel channel = SessionSocketHolder.get(message.getToUserId());
        if (channel == null || !channel.isActive()) { saveOffline(message, "channel offline"); return; }
        try {
            channel.writeAndFlush(new TIMReqMsg(requestId(message.getMessageId()), json.writeValueAsString(message), Constants.CommandType.CHAT));
            pending.putIfAbsent(message.getMessageId(), new PendingDelivery(message, System.currentTimeMillis() + retryMs));
        } catch (Exception ex) { saveOffline(message, "push failed"); }
    }
    private long requestId(String messageId) {
        try { return Long.parseLong(messageId); }
        catch (NumberFormatException ignored) { return Integer.toUnsignedLong(messageId.hashCode()); }
    }
    public void acknowledge(String messageId) {
        pending.remove(messageId);
        history.updateStatus(messageId, "ACKED");
    }
    @Scheduled(fixedDelayString = "${tim.delivery.scan-ms:1000}")
    void retryPending() {
        long now = System.currentTimeMillis();
        pending.forEach((id, delivery) -> {
            if (delivery.getNextRetryAt() > now) return;
            if (delivery.getAttempts() >= maxRetries) { pending.remove(id); saveOffline(delivery.getMessage(), "retry exhausted"); return; }
            delivery.incrementAttempts(now + retryMs);
            dispatch(delivery.getMessage());
        });
    }

    @Scheduled(fixedDelayString = "${tim.outbox.scan-ms:1000}")
    void relayOutbox() {
        for (OutboxRepository.OutboxEvent event : outbox.pending(100)) {
            try {
                ChatMessage message = json.readValue(event.payload(), ChatMessage.class);
                dispatch(message);
                outbox.sent(event.eventId());
            } catch (Exception e) {
                outbox.retry(event.eventId(), e.getClass().getSimpleName());
            }
        }
    }
    public void saveOffline(ChatMessage message, String reason) {
        try {
            String key = "im:offline:" + message.getToUserId();
            // messageId is the idempotent member; a per-user cursor is assigned by Redis.
            Long cursor = redis.opsForValue().increment("im:offline:cursor:" + message.getToUserId());
            long deliveryCursor = cursor == null ? 0L : cursor;
            redis.opsForZSet().add(key, message.getMessageId(), (double) deliveryCursor);
            redis.expire(key, Duration.ofDays(7));
            history.indexOffline(message.getToUserId(), deliveryCursor, message.getMessageId());
            Long size = redis.opsForZSet().zCard(key);
            if (size != null && size > offlineLimit) redis.opsForZSet().removeRange(key, 0, size - offlineLimit - 1);
            history.insertIfAbsent(message, "OFFLINE");
        } catch (Exception ignored) { history.insertIfAbsent(message, "OFFLINE"); }
    }
    public List<OfflineMessage> pullOffline(long userId, long cursor, int limit) {
        String key = "im:offline:" + userId;
        List<String> ids = new ArrayList<>(redis.opsForZSet().rangeByScore(key, cursor + 1, Double.MAX_VALUE, 0, limit));
        List<OfflineMessage> result = new ArrayList<>(history.findOfflineBodies(userId, ids));
        if (result.size() < limit) result.addAll(history.findOfflineRecordsAfter(userId, cursor, limit - result.size()));
        return result;
    }
    /** Advance only after the client has processed the largest continuous cursor. */
    public void acknowledgeOffline(long userId, long cursor) {
        if (cursor <= 0) return;
        redis.opsForZSet().removeRangeByScore("im:offline:" + userId, Double.NEGATIVE_INFINITY, cursor);
    }
}
