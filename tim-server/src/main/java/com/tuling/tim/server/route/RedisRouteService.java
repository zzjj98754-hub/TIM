package com.tuling.tim.server.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** The one routing source of truth for the demo: Redis user -> IM node. */
@Service
public class RedisRouteService {
    private static final DefaultRedisScript<Long> ONLINE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('HSET', KEYS[1], 'nodeId', ARGV[1], 'sessionId', ARGV[2], 'epoch', ARGV[3], 'route', ARGV[4]); " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[5]); " +
                    "redis.call('SET', KEYS[2], '1', 'EX', ARGV[5]); return 1", Long.class);
    private static final DefaultRedisScript<Long> OFFLINE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'sessionId') == ARGV[1] and redis.call('HGET', KEYS[1], 'epoch') == ARGV[2] then " +
                    "redis.call('DEL', KEYS[1], KEYS[2]); return 1 else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'nodeId') == ARGV[1] and redis.call('HGET', KEYS[1], 'sessionId') == ARGV[2] and redis.call('HGET', KEYS[1], 'epoch') == ARGV[3] then " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[4]); redis.call('EXPIRE', KEYS[2], ARGV[4]); return 1 else return 0 end", Long.class);
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
        long ttlSeconds = Duration.ofHours(24).getSeconds();
        redis.execute(ONLINE_SCRIPT, java.util.List.of(routeKey(userId), presenceKey(userId)),
                serverId, sessionId, String.valueOf(epoch), routeInfo, String.valueOf(ttlSeconds));
    }
    public void offline(long userId, String sessionId, long epoch) {
        redis.execute(OFFLINE_SCRIPT, java.util.List.of(routeKey(userId), presenceKey(userId)),
                sessionId, String.valueOf(epoch));
    }
    public boolean renew(long userId, String sessionId, long epoch) {
        long ttlSeconds = Duration.ofHours(24).getSeconds();
        Long result = redis.execute(RENEW_SCRIPT, java.util.List.of(routeKey(userId), presenceKey(userId)),
                serverId, sessionId, String.valueOf(epoch), String.valueOf(ttlSeconds));
        return Long.valueOf(1L).equals(result);
    }
    public String findServer(long userId) { return (String) redis.opsForHash().get(routeKey(userId), "nodeId"); }
    public String serverId() { return serverId; }
    private String routeKey(long userId) { return "tim:route:user:" + userId; }
    private String presenceKey(long userId) { return "tim:presence:user:" + userId; }

}
