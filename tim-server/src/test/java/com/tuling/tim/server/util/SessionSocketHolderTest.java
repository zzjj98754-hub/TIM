package com.tuling.tim.server.util;

import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSocketHolderTest {
    @Test
    void closingOldConnectionMustNotRemoveReplacement() {
        NioSocketChannel oldChannel = new NioSocketChannel();
        NioSocketChannel newChannel = new NioSocketChannel();
        try {
            SessionSocketHolder.put(90001L, oldChannel);
            SessionSocketHolder.put(90001L, newChannel);
            SessionSocketHolder.saveSession(90001L, "new-session");

            SessionSocketHolder.remove(oldChannel);

            assertSame(newChannel, SessionSocketHolder.get(90001L));
            assertTrue(SessionSocketHolder.isCurrent(90001L, newChannel));
            org.junit.jupiter.api.Assertions.assertEquals("new-session", SessionSocketHolder.getUserId(newChannel).getUserName());
        } finally {
            SessionSocketHolder.remove(oldChannel);
            SessionSocketHolder.remove(newChannel);
        }
    }
}
