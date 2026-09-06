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
import java.util.UUID;

/** Main learning path: route -> node transport -> local push -> ACK -> bounded retry/offline. */
@Service
public class ReliableMessageService {
    private final RedisRouteService routes;
    private final StringRedisTemplate redis;
    private final MessageHistoryRepository history;
    private final ObjectMapper json;
    private final SnowflakeIdGenerator ids;
    private final OutboxRepository outbox;
    private final DeliveryRepository deliveries;
    private final Map<String, PendingDelivery> pending = new ConcurrentHashMap<>();
    private NodeMessageBus bus;
    private final int maxRetries;
    private final long retryMs;
    private final int offlineLimit;
    private final int outboxMaxRetries;
    private final String deliveryWorkerId = UUID.randomUUID().toString();

    public ReliableMessageService(RedisRouteService routes, StringRedisTemplate redis, MessageHistoryRepository history,
                                  ObjectMapper json, SnowflakeIdGenerator ids, OutboxRepository outbox,
                                  int maxRetries, long retryMs, int offlineLimit) {
        this(routes, redis, history, json, ids, outbox, null, maxRetries, retryMs, offlineLimit, 10);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReliableMessageService(RedisRouteService routes, StringRedisTemplate redis, MessageHistoryRepository history,
                                  ObjectMapper json, SnowflakeIdGenerator ids, OutboxRepository outbox,
                                  DeliveryRepository deliveries,
                                  @Value("${tim.delivery.max-retries:3}") int maxRetries,
                                  @Value("${tim.delivery.retry-ms:5000}") long retryMs,
                                  @Value("${tim.offline.max-size:1000}") int offlineLimit,
                                  @Value("${tim.outbox.max-retries:10}") int outboxMaxRetries) {
        this.routes = routes; this.redis = redis; this.history = history; this.json = json; this.ids = ids; this.outbox = outbox; this.deliveries = deliveries;
        this.maxRetries = maxRetries; this.retryMs = retryMs; this.offlineLimit = offlineLimit; this.outboxMaxRetries = outboxMaxRetries;
    }
    @org.springframework.beans.factory.annotation.Autowired
    void setBus(NodeMessageBus bus) { this.bus = bus; }

    @Transactional
    public void accept(ChatMessage message) {
        if (message.getMessageId() == null || message.getMessageId().isEmpty()) message.setMessageId(ids.nextId());
        if (message.getCreatedAt() == 0) message.setCreatedAt(System.currentTimeMillis());
        if (message.getClientMessageId() == null || message.getClientMessageId().isBlank()) message.setClientMessageId(message.getMessageId());
        String dedupKey = "tim:dedup:message:" + message.getMessageId();
        Boolean firstSeen = redis.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(firstSeen)) return;
        try {
            boolean inserted = history.insertIfAbsent(message, "PENDING");
            if (!inserted) return;
            if (deliveries != null) deliveries.createPending(message);
            outbox.append(message);
        } catch (RuntimeException e) {
            redis.delete(dedupKey);
            throw e;
        }
    }

    public void receiveFromNode(ChatMessage message) { deliverLocalOrOffline(message); }
    public void replayOffline(long userId, long cursor, io.netty.channel.Channel channel) {
        for (OfflineMessage offline : pullOffline(userId, cursor, 100)) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = json.readTree(offline.getBody());
                if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                    object.put("deliveryCursor", offline.getDeliveryCursor());
                    channel.writeAndFlush(new TIMReqMsg(requestId(offline.getMessageId()), json.writeValueAsString(object), Constants.CommandType.CHAT));
                }
            } catch (Exception e) { throw new IllegalStateException("offline replay failed", e); }
        }
    }
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
            if (deliveries != null) deliveries.markDelivering(message.getMessageId(), message.getToUserId(), System.currentTimeMillis() + retryMs);
            pending.putIfAbsent(message.getMessageId(), new PendingDelivery(message, System.currentTimeMillis() + retryMs));
        } catch (Exception ex) { saveOffline(message, "push failed"); }
    }
    private long requestId(String messageId) {
        try { return Long.parseLong(messageId); }
        catch (NumberFormatException ignored) { return Integer.toUnsignedLong(messageId.hashCode()); }
    }
    public void acknowledge(String messageId) { acknowledge(messageId, -1L); }
    public void acknowledge(String messageId, long recipientId) {
        pending.remove(messageId);
        if (deliveries == null || recipientId <= 0 || deliveries.acknowledge(messageId, recipientId)) {
            history.updateStatus(messageId, "ACKED");
        }
    }
    @Scheduled(fixedDelayString = "${tim.delivery.scan-ms:1000}")
    void retryPending() {
        long now = System.currentTimeMillis();
        pending.forEach((id, delivery) -> {
            if (delivery.getNextRetryAt() > now) return;
            if (delivery.getAttempts() >= maxRetries) { pending.remove(id); if (deliveries != null) deliveries.markOffline(delivery.getMessage().getMessageId(), delivery.getMessage().getToUserId()); saveOffline(delivery.getMessage(), "retry exhausted"); return; }
            delivery.incrementAttempts(now + retryMs);
            dispatch(delivery.getMessage());
        });
        if (deliveries != null) {
            for (DeliveryRepository.DeliveryCandidate candidate : deliveries.claimDue(100, deliveryWorkerId, retryMs + 60_000L)) {
                if (!pending.containsKey(candidate.messageId())) dispatch(deliveries.readMessage(candidate));
            }
        }
    }

    @Scheduled(fixedDelayString = "${tim.outbox.scan-ms:1000}")
    void relayOutbox() {
        for (OutboxRepository.OutboxEvent event : outbox.claimPending(100)) {
            try {
                ChatMessage message = json.readValue(event.payload(), ChatMessage.class);
                if ("GROUP_MESSAGE_CREATED".equals(event.eventType())) bus.broadcastGroup(message);
                else dispatch(message);
                outbox.sent(event.eventId());
            } catch (Exception e) {
                outbox.retry(event.eventId(), e.getClass().getSimpleName(), outboxMaxRetries);
            }
        }
    }
    public void saveOffline(ChatMessage message, String reason) {
        try {
            String key = "im:offline:" + message.getToUserId();
            Long existingCursor = history.findOfflineCursor(message.getToUserId(), message.getMessageId());
            if (existingCursor != null && existingCursor > 0L) {
                redis.opsForZSet().add(key, message.getMessageId(), existingCursor.doubleValue());
                redis.expire(key, Duration.ofDays(7));
                if (deliveries != null) deliveries.markOffline(message.getMessageId(), message.getToUserId());
                return;
            }
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
        if (deliveries != null) deliveries.markOffline(message.getMessageId(), message.getToUserId());
    }
    public List<OfflineMessage> pullOffline(long userId, long cursor, int limit) {
        String key = "im:offline:" + userId;
        List<String> ids = new ArrayList<>(redis.opsForZSet().rangeByScore(key, cursor + 1, Double.MAX_VALUE, 0, limit));
        List<OfflineMessage> result = new ArrayList<>(history.findOfflineBodies(userId, ids));
        if (result.size() < limit) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (OfflineMessage message : result) seen.add(message.getMessageId());
            for (OfflineMessage message : history.findOfflineRecordsAfter(userId, cursor, limit)) {
                if (seen.add(message.getMessageId())) result.add(message);
                if (result.size() >= limit) break;
            }
        }
        return result;
    }
    /** Advance only after the client has processed the largest continuous cursor. */
    public void acknowledgeOffline(long userId, long cursor) {
        if (cursor <= 0) return;
        if (history.acknowledgeOffline(userId, cursor)) {
            redis.opsForZSet().removeRangeByScore("im:offline:" + userId, Double.NEGATIVE_INFINITY, cursor);
        }
    }
}
