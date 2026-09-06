package com.tuling.tim.server.mq;

import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.message.ReliableMessageService;
import com.tuling.tim.server.group.GroupChannelPushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;

/** Lets two server contexts in one JVM demonstrate cross-node dispatch without RocketMQ. */
@Service
@Primary
@ConditionalOnProperty(name = "tim.mq.mode", havingValue = "local", matchIfMissing = true)
public class LocalNodeMessageBus implements NodeMessageBus {
    private static final ConcurrentHashMap<String, ReliableMessageService> NODES = new ConcurrentHashMap<>();
    private final ReliableMessageService messages;
    private final String serverId;
    private final GroupChannelPushService groups;

    public LocalNodeMessageBus(ReliableMessageService messages, GroupChannelPushService groups, @Value("${tim.server.id:im-server-1}") String serverId) {
        this.messages = messages; this.groups = groups; this.serverId = serverId;
    }
    @PostConstruct
    void register() { NODES.put(serverId, messages); }
    @Override public void forward(String targetServerId, ChatMessage message) {
        ReliableMessageService target = NODES.get(targetServerId);
        if (target != null) target.receiveFromNode(message);
        else messages.saveOffline(message, "target node unavailable");
    }
    @Override public void broadcastGroup(ChatMessage message) { groups.push(message); }
}
