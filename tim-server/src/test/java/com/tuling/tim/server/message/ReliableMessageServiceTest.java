package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.server.route.RedisRouteService;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class ReliableMessageServiceTest {
    @Test
    void recoveredDeliveryAtAttemptLimitMovesOfflineInsteadOfRetryingForever() throws Exception {
        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        ChatMessage recovered = message("recovered-at-limit");
        String body = new ObjectMapper().writeValueAsString(recovered);
        when(deliveries.claimDue(anyInt(), anyString(), anyLong())).thenReturn(java.util.List.of(
                new DeliveryRepository.DeliveryCandidate(recovered.getMessageId(), recovered.getToUserId(), body, 3)));
        when(deliveries.readMessage(any())).thenReturn(recovered);
        RedisRouteService routes = mock(RedisRouteService.class);
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        ReliableMessageService service = new ReliableMessageService(routes, mock(StringRedisTemplate.class), history,
                new ObjectMapper(), new SnowflakeIdGenerator(), mock(OutboxRepository.class), deliveries,
                3, 5000L, 100, 10);

        service.retryPending();

        verify(deliveries).markOffline(recovered.getMessageId(), recovered.getToUserId());
        verify(routes, never()).findServer(anyLong());
    }

    @Test
    void exposesDurableReliabilityGauges() {
        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        when(deliveries.pendingCount()).thenReturn(7L);
        when(deliveries.oldestPendingSeconds()).thenReturn(12.5D);
        OutboxRepository outbox = mock(OutboxRepository.class);
        when(outbox.pendingCount()).thenReturn(3L);
        when(outbox.deadCount()).thenReturn(2L);
        ReliableMessageService service = new ReliableMessageService(mock(RedisRouteService.class), mock(StringRedisTemplate.class),
                mock(MessageHistoryRepository.class), new ObjectMapper(), new SnowflakeIdGenerator(), outbox, deliveries,
                3, 5000L, 100, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        service.setMetrics(registry);

        assertEquals(7D, registry.get("tim_delivery_pending").gauge().value());
        assertEquals(12.5D, registry.get("tim_delivery_oldest_seconds").gauge().value());
        assertEquals(3D, registry.get("tim_outbox_pending").gauge().value());
        assertEquals(2D, registry.get("tim_outbox_dead").gauge().value());
        assertEquals(0D, registry.get("tim_message_dead_total").counter().count());
    }

    @Test
    void unacknowledgedMessageIsRetried() {
        RedisRouteService routes = mock(RedisRouteService.class);
        when(routes.findServer(2L)).thenReturn("node-1");
        when(routes.serverId()).thenReturn("node-1");
        ReliableMessageService service = newService(routes, 0L);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            SessionSocketHolder.put(2L, channel);
            ChatMessage message = message("retry-me");

            service.receiveFromNode(message);
            assertNotNull(channel.readOutbound());
            service.retryPending();
            assertNotNull(channel.readOutbound());
        } finally {
            SessionSocketHolder.remove(channel);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void acknowledgedMessageIsNotRetried() {
        RedisRouteService routes = mock(RedisRouteService.class);
        when(routes.findServer(2L)).thenReturn("node-1");
        when(routes.serverId()).thenReturn("node-1");
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        ReliableMessageService service = newService(routes, 0L, history);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            SessionSocketHolder.put(2L, channel);
            ChatMessage message = message("ack-me");

            service.receiveFromNode(message);
            assertNotNull(channel.readOutbound());
            service.acknowledge(message.getMessageId());
            service.retryPending();

            verify(history).updateStatus(message.getMessageId(), "ACKED");
            org.junit.jupiter.api.Assertions.assertNull(channel.readOutbound());
        } finally {
            SessionSocketHolder.remove(channel);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void databaseDedupRejectsSecondMessageAfterRedisCacheIsRemoved() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        when(history.insertIfAbsent(any(ChatMessage.class), eq("PENDING"))).thenReturn(true, false);
        ReliableMessageService service = new ReliableMessageService(mock(RedisRouteService.class), redis, history,
                new ObjectMapper(), new SnowflakeIdGenerator(), mock(OutboxRepository.class), 3, 5000L, 100);
        ChatMessage message = message("dedup-me");

        service.accept(message);
        service.accept(message);

        verify(history, times(2)).insertIfAbsent(message, "PENDING");
        verify(values).set("tim:dedup:message:dedup-me", "1", java.time.Duration.ofHours(24));
    }

    private ReliableMessageService newService(RedisRouteService routes, long retryMs) {
        return newService(routes, retryMs, mock(MessageHistoryRepository.class));
    }

    private ReliableMessageService newService(RedisRouteService routes, long retryMs, MessageHistoryRepository history) {
        return new ReliableMessageService(routes, mock(StringRedisTemplate.class), history,
                new ObjectMapper(), new SnowflakeIdGenerator(), mock(OutboxRepository.class),
                3, retryMs, 100);
    }

    private ChatMessage message(String id) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(id);
        message.setToUserId(2L);
        message.setFromUserId(1L);
        message.setContent("hello");
        return message;
    }
}
