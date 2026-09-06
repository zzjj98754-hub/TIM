package com.tuling.tim.server.handle;

import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.util.ConnectionSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TIMServerHandleTest {
    @Test
    void messageBodySenderIsReplacedByAuthenticatedSession() {
        ChatMessage message = new ChatMessage();
        message.setFromUserId(999L);

        TIMServerHandle.trustSessionIdentity(message, new ConnectionSession(42L, "session", 7L));

        assertEquals(42L, message.getFromUserId());
    }
}
