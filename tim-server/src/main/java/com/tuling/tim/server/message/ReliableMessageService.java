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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tuling.tim.server.web.WebSocketSessionRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Main learning path: route -> node transport -> local push -> ACK -> bounded retry/offline. */
@Service
public class ReliableMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReliableMessageService.class);
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
    private final int outboxMaxRetries;
    private final String deliveryWorkerId = UUID.randomUUID().toString();
    private final int offlineLimit;
    private MeterRegistry metrics;

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
    void setBus(@Lazy NodeMessageBus bus) { this.bus = bus; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMetrics(MeterRegistry metrics) {
        this.metrics = metrics;
        for (String counter : List.of("tim_message_accept_total", "tim_message_ack_total", "tim_message_retry_total",
                "tim_message_offline_total", "tim_message_dead_total")) metrics.counter(counter);
        io.micrometer.core.instrument.Gauge.builder("tim_delivery_pending", this, ReliableMessageService::deliveryPending).register(metrics);
        io.micrometer.core.instrument.Gauge.builder("tim_delivery_oldest_seconds", this, ReliableMessageService::deliveryOldestSeconds).register(metrics);
        io.micrometer.core.instrument.Gauge.builder("tim_outbox_pending", this, ReliableMessageService::outboxPending).register(metrics);
        io.micrometer.core.instrument.Gauge.builder("tim_outbox_dead", this, ReliableMessageService::outboxDead).register(metrics);
    }
    private void count(String name) { if (metrics != null) metrics.counter(name).increment(); }
    private double deliveryPending() { return metricValue(() -> deliveries == null ? pending.size() : deliveries.pendingCount()); }
    private double deliveryOldestSeconds() { return metricValue(() -> deliveries == null ? 0D : deliveries.oldestPendingSeconds()); }
    private double outboxPending() { return metricValue(outbox::pendingCount); }
    private double outboxDead() { return metricValue(outbox::deadCount); }
    private double metricValue(java.util.function.DoubleSupplier supplier) {
        try { return supplier.getAsDouble(); }
        catch (RuntimeException e) {
            LOGGER.warn("reliability gauge query failed", e);
            return Double.NaN;
        }
    }

    @Transactional
    public void accept(ChatMessage message) {
        if (message.getMessageId() == null || message.getMessageId().isEmpty()) message.setMessageId(ids.nextId());
        if (message.getCreatedAt() == 0) message.setCreatedAt(System.currentTimeMillis());
        if (message.getClientMessageId() == null || message.getClientMessageId().isBlank()) message.setClientMessageId(message.getMessageId());
        boolean inserted = history.insertIfAbsent(message, "PENDING");
        if (!inserted) return;
        count("tim_message_accept_total");
        if (deliveries != null) deliveries.createPending(message);
        outbox.append(message);
        Runnable cacheDedup = () -> {
            try { redis.opsForValue().set("tim:dedup:message:" + message.getMessageId(), "1", Duration.ofHours(24)); }
            catch (RuntimeException e) { LOGGER.warn("message dedup cache refresh failed messageId={}", message.getMessageId(), e); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cacheDedup.run(); }
            });
        } else {
            cacheDedup.run();
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
        history.markRouting(message.getMessageId());
        String target = routes.findServer(message.getToUserId());
        if (target == null) { saveOffline(message, "recipient offline"); return; }
        if (routes.serverId().equals(target)) deliverLocalOrOffline(message); else bus.forward(target, message);
    }
    private void deliverLocalOrOffline(ChatMessage message) {
        Channel channel = SessionSocketHolder.get(message.getToUserId());
        WebSocketSession web = WebSocketSessionRegistry.get(message.getToUserId());
        if ((channel == null || !channel.isActive()) && web != null && web.isOpen()) {
            try {
                web.sendMessage(new TextMessage(json.writeValueAsString(java.util.Map.of("type", "CHAT_MESSAGE", "messageId", message.getMessageId(), "clientMessageId", message.getClientMessageId(), "senderId", message.getFromUserId(), "receiverId", message.getToUserId(), "content", message.getContent(), "timestamp", message.getCreatedAt()))));
                if (deliveries != null) deliveries.markDelivering(message.getMessageId(), message.getToUserId(), System.currentTimeMillis() + retryMs);
                history.markDelivering(message.getMessageId(), message.getToUserId());
                pending.putIfAbsent(message.getMessageId(), new PendingDelivery(message, System.currentTimeMillis() + retryMs));
                return;
            } catch (Exception ex) { saveOffline(message, "websocket push failed"); return; }
        }
        if (channel == null || !channel.isActive()) { saveOffline(message, "channel offline"); return; }
        try {
            channel.writeAndFlush(new TIMReqMsg(requestId(message.getMessageId()), json.writeValueAsString(message), Constants.CommandType.CHAT));
            if (deliveries != null) deliveries.markDelivering(message.getMessageId(), message.getToUserId(), System.currentTimeMillis() + retryMs);
            history.markDelivering(message.getMessageId(), message.getToUserId());
            pending.putIfAbsent(message.getMessageId(), new PendingDelivery(message, System.currentTimeMillis() + retryMs));
        } catch (Exception ex) { saveOffline(message, "push failed"); }
    }
    private long requestId(String messageId) {
        try { return Long.parseLong(messageId); }
        catch (NumberFormatException ignored) { return Integer.toUnsignedLong(messageId.hashCode()); }
    }
    @Transactional
    public void acknowledge(String messageId, long recipientId) {
        pending.remove(messageId);
        if (deliveries == null || recipientId <= 0) return;
        if (!deliveries.acknowledge(messageId, recipientId)) return;
        if (!history.acknowledge(messageId, recipientId)) throw new IllegalStateException("message ACK state mismatch: " + messageId);
        count("tim_message_ack_total");
    }
    @Scheduled(fixedDelayString = "${tim.delivery.scan-ms:1000}")
    void retryPending() {
        long now = System.currentTimeMillis();
        pending.forEach((id, delivery) -> {
            if (delivery.getNextRetryAt() > now) return;
            if (delivery.getAttempts() >= maxRetries) { pending.remove(id); if (deliveries != null) deliveries.markOffline(delivery.getMessage().getMessageId(), delivery.getMessage().getToUserId()); saveOffline(delivery.getMessage(), "retry exhausted"); return; }
            delivery.incrementAttempts(now + retryMs);
            count("tim_message_retry_total");
            dispatch(delivery.getMessage());
        });
        if (deliveries != null) {
            for (DeliveryRepository.DeliveryCandidate candidate : deliveries.claimDue(100, deliveryWorkerId, retryMs + 60_000L)) {
                if (pending.containsKey(candidate.messageId())) continue;
                ChatMessage recovered = deliveries.readMessage(candidate);
                if (candidate.attemptCount() >= maxRetries) saveOffline(recovered, "durable retry exhausted");
                else dispatch(recovered);
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
        count("tim_message_offline_total");
        history.insertIfAbsent(message, DeliveryStatus.OFFLINE.name());
        long deliveryCursor = history.indexOffline(message.getToUserId(), message.getMessageId());
        if (deliveries != null) deliveries.markOffline(message.getMessageId(), message.getToUserId());
        try {
            String key = "im:offline:" + message.getToUserId();
            redis.opsForZSet().add(key, message.getMessageId(), (double) deliveryCursor);
            redis.expire(key, Duration.ofDays(7));
            Long size = redis.opsForZSet().zCard(key);
            if (size != null && size > offlineLimit) redis.opsForZSet().removeRange(key, 0, size - offlineLimit - 1);
        } catch (RuntimeException e) {
            LOGGER.warn("offline Redis projection deferred messageId={} recipientId={} cursor={}", message.getMessageId(), message.getToUserId(), deliveryCursor, e);
        }
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
    @Scheduled(fixedDelayString = "${tim.offline.rebuild-ms:60000}")
    void rebuildOfflineProjection() {
        try {
            for (Long userId : history.findOfflineUsers(1000)) {
                String key = "im:offline:" + userId;
                for (OfflineMessage message : history.findRecentOfflineRecords(userId, offlineLimit)) {
                    redis.opsForZSet().add(key, message.getMessageId(), message.getDeliveryCursor());
                }
                redis.expire(key, Duration.ofDays(7));
            }
        } catch (Exception e) {
            LOGGER.warn("offline Redis projection rebuild deferred", e);
        }
    }
}
