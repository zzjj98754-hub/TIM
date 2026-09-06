package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.server.route.RedisRouteService;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class ReliableMessageServiceTest {
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
