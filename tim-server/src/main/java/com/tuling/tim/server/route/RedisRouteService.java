package com.tuling.tim.server.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;

/** The one routing source of truth for the demo: Redis user -> IM node. */
@Service
public class RedisRouteService {
    private final StringRedisTemplate redis;
    private final String serverId;
    private final String routeInfo;

    public RedisRouteService(StringRedisTemplate redis, @Value("${tim.server.id:im-server-1}") String serverId,
                             @Value("${tim.server.host:127.0.0.1}") String host,
                             @Value("${tim.server.port:9002}") int tcpPort,
                             @Value("${server.port:8082}") int httpPort) {
        this.redis = redis; this.serverId = serverId; this.routeInfo = host + ":" + tcpPort + ":" + httpPort;
    }
    public void online(long userId, String sessionId, long epoch) {
        String key = routeKey(userId);
        redis.opsForHash().putAll(key, MapBuilder.of("nodeId", serverId, "sessionId", sessionId, "epoch", String.valueOf(epoch), "route", routeInfo));
        redis.expire(key, Duration.ofHours(24));
        redis.opsForValue().set("tim:presence:user:" + userId, "1", Duration.ofHours(24));
    }
    public void online(long userId) { online(userId, "legacy", System.currentTimeMillis()); }
    public void offline(long userId, String sessionId, long epoch) {
        String key = routeKey(userId);
        String currentSession = (String) redis.opsForHash().get(key, "sessionId");
        String currentEpoch = (String) redis.opsForHash().get(key, "epoch");
        if (Objects.equals(sessionId, currentSession) && Objects.equals(String.valueOf(epoch), currentEpoch)) {
            redis.delete(key);
            redis.delete("tim:presence:user:" + userId);
        }
    }
    public String findServer(long userId) { return (String) redis.opsForHash().get(routeKey(userId), "nodeId"); }
    public String serverId() { return serverId; }
    private String routeKey(long userId) { return "tim:route:user:" + userId; }

    private static final class MapBuilder {
        static java.util.Map<String, String> of(String k1, String v1, String k2, String v2, String k3, String v3, String k4, String v4) {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put(k1, v1); map.put(k2, v2); map.put(k3, v3); map.put(k4, v4); return map;
        }
    }
}
