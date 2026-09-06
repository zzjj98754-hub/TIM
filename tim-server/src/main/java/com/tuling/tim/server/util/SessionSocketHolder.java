package com.tuling.tim.server.util;

import com.tuling.tim.common.pojo.TIMUserInfo;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @since JDK 1.8
 */
public class SessionSocketHolder {
    private static final Map<Long, NioSocketChannel> CHANNEL_MAP = new ConcurrentHashMap<>(16);
    private static final Map<Long, String> SESSION_MAP = new ConcurrentHashMap<>(16);
    private static final Map<NioSocketChannel, ConnectionSession> CONNECTIONS = new ConcurrentHashMap<>(16);
    private static final Map<Long, Set<NioSocketChannel>> GROUP_CHANNELS = new ConcurrentHashMap<>(16);

    public static void saveSession(Long userId, String userName) {
        SESSION_MAP.put(userId, userName);
    }

    public static void removeSession(Long userId) {
        SESSION_MAP.remove(userId);
    }

    /**
     * Save the relationship between the userId and the channel.
     *
     * @param id
     * @param socketChannel
     */
    public static String put(Long id, NioSocketChannel socketChannel) {
        String sessionId = UUID.randomUUID().toString();
        long epoch = System.currentTimeMillis();
        CHANNEL_MAP.put(id, socketChannel);
        CONNECTIONS.put(socketChannel, new ConnectionSession(id, sessionId, epoch));
        return sessionId + ":" + epoch;
    }

    public static NioSocketChannel get(Long id) {
        return CHANNEL_MAP.get(id);
    }

    public static Map<Long, NioSocketChannel> getRelationShip() {
        return CHANNEL_MAP;
    }

    public static void remove(NioSocketChannel nioSocketChannel) {
        ConnectionSession session = CONNECTIONS.remove(nioSocketChannel);
        if (session != null) CHANNEL_MAP.remove(session.getUserId(), nioSocketChannel);
        GROUP_CHANNELS.values().forEach(channels -> channels.remove(nioSocketChannel));
    }

    public static ConnectionSession getSession(NioSocketChannel channel) {
        return CONNECTIONS.get(channel);
    }

    public static boolean isCurrent(long userId, NioSocketChannel channel) {
        return CHANNEL_MAP.get(userId) == channel && CONNECTIONS.containsKey(channel);
    }

    public static void joinGroup(long groupId, NioSocketChannel channel) {
        GROUP_CHANNELS.computeIfAbsent(groupId, ignored -> ConcurrentHashMap.newKeySet()).add(channel);
    }
    public static void leaveGroup(long groupId, NioSocketChannel channel) {
        Set<NioSocketChannel> channels = GROUP_CHANNELS.get(groupId);
        if (channels != null) { channels.remove(channel); if (channels.isEmpty()) GROUP_CHANNELS.remove(groupId, channels); }
    }
    public static Set<NioSocketChannel> groupChannels(long groupId) {
        return GROUP_CHANNELS.getOrDefault(groupId, Collections.emptySet());
    }

    /**
     * 获取注册用户信息
     *
     * @param nioSocketChannel
     * @return
     */
    public static TIMUserInfo getUserId(NioSocketChannel nioSocketChannel) {
        for (Map.Entry<Long, NioSocketChannel> entry : CHANNEL_MAP.entrySet()) {
            NioSocketChannel value = entry.getValue();
            if (nioSocketChannel == value) {
                Long key = entry.getKey();
                String userName = SESSION_MAP.get(key);
                TIMUserInfo info = new TIMUserInfo(key, userName);
                return info;
            }
        }

        return null;
    }


}
