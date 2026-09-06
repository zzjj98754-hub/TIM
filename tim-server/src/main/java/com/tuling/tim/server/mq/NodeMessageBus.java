package com.tuling.tim.server.mq;

import com.tuling.tim.server.message.ChatMessage;

/** Cross-node transport boundary. Local bus is the no-broker development fallback. */
public interface NodeMessageBus {
    void forward(String targetServerId, ChatMessage message);
    default void broadcastGroup(ChatMessage message) { }
}
