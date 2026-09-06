package com.tuling.tim.server.handle;

import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.util.ConnectionSession;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TIMServerHandleTest {
    @Test
    void messageBodySenderIsReplacedByAuthenticatedSession() {
        ChatMessage message = new ChatMessage();
        message.setFromUserId(999L);

        TIMServerHandle.trustSessionIdentity(message, new ConnectionSession(42L, "session", 7L));

        assertEquals(42L, message.getFromUserId());
    }

    @Test
    void businessFrameRequiresAuthenticatedSession() {
        assertFalse(TIMServerHandle.isAuthenticated(null, new EmbeddedChannel()));
        EmbeddedChannel channel = new EmbeddedChannel();
        assertFalse(TIMServerHandle.isAuthenticated(new ConnectionSession(42L, "session", 7L), channel));
        SessionSocketHolder.put(42L, channel);
        assertTrue(TIMServerHandle.isAuthenticated(SessionSocketHolder.getSession(channel), channel));
    }

    @Test
    void rejectsMissingOrOversizedMessageContent() {
        ChatMessage message = new ChatMessage();
        message.setContent("ok");
        assertTrue(TIMServerHandle.contentWithinLimit(message, 2));
        assertFalse(TIMServerHandle.contentWithinLimit(message, 1));
        message.setContent(null);
        assertFalse(TIMServerHandle.contentWithinLimit(message, 100));
    }
}
