package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.server.route.RedisRouteService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfflineMessageServiceTest {
    @Test
    void offlineIndexIsTrimmedToConfiguredCapacity() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zsets);
        when(values.increment(anyString())).thenReturn(3L);
        when(zsets.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(zsets.zCard(anyString())).thenReturn(3L);
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        ReliableMessageService service = newService(redis, history, 2);

        ChatMessage message = message("offline-3");
        service.saveOffline(message, "recipient offline");

        verify(zsets).removeRange("im:offline:2", 0L, 0L);
        verify(history).indexOffline(2L, 3L, "offline-3");
    }

    @Test
    void pullUsesRedisCursorIdsAndLoadsDurableBodies() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zsets);
        when(zsets.rangeByScore("im:offline:2", 6D, Double.MAX_VALUE, 0, 2))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.Collections.singletonList("m7")));
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        when(history.findOfflineBodies(2L, java.util.Collections.singletonList("m7")))
                .thenReturn(java.util.Collections.singletonList(new OfflineMessage("m7", 7L, "body-7")));
        ReliableMessageService service = newService(redis, history, 100);

        java.util.List<OfflineMessage> result = service.pullOffline(2L, 5L, 2);
        assertEquals(1, result.size());
        assertEquals("body-7", result.get(0).getBody());
        assertEquals(7L, result.get(0).getDeliveryCursor());
        verify(history).findOfflineBodies(2L, java.util.Collections.singletonList("m7"));
    }

    private ReliableMessageService newService(StringRedisTemplate redis, MessageHistoryRepository history, int limit) {
        return new ReliableMessageService(mock(RedisRouteService.class), redis, history,
                new ObjectMapper(), new SnowflakeIdGenerator(), mock(OutboxRepository.class),
                3, 5000L, limit);
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
