package com.tuling.tim.server.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.stereotype.Service;

@Service
public class GroupChannelPushService {
    private final ObjectMapper json;
    public GroupChannelPushService(ObjectMapper json) { this.json = json; }
    public void push(ChatMessage message) {
        try {
            String body = json.writeValueAsString(message);
            for (NioSocketChannel channel : SessionSocketHolder.groupChannels(message.getGroupId())) {
                if (channel.isActive()) channel.writeAndFlush(new TIMReqMsg(Long.parseLong(message.getMessageId()), body, Constants.CommandType.GROUP_CHAT));
            }
        } catch (Exception e) { throw new IllegalStateException("group broadcast push failed", e); }
    }
}
