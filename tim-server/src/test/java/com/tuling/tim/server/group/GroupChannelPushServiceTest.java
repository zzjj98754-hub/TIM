package com.tuling.tim.server.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupChannelPushServiceTest {
    @Test
    void broadcastPushesOnlyToLocalGroupChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            SessionSocketHolder.put(91001L, channel);
            SessionSocketHolder.joinGroup(77L, channel);
            ChatMessage message = new ChatMessage();
            message.setMessageId("group-message-1");
            message.setGroupId(77L);
            message.setFromUserId(1L);
            message.setContent("hello");

            new GroupChannelPushService(new ObjectMapper()).push(message);

            TIMReqMsg pushed = channel.readOutbound();
            assertNotNull(pushed);
            assertEquals(Constants.CommandType.GROUP_CHAT, pushed.getType());
            assertNotNull(pushed.getRequestId());
            assertTrue(pushed.getReqMsg().contains("group-message-1"));
        } finally {
            SessionSocketHolder.remove(channel);
            channel.finishAndReleaseAll();
        }
    }
}
