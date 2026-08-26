package com.tuling.tim.server.init;

import com.tuling.tim.common.protocol.ObjDecoder;
import com.tuling.tim.common.protocol.ObjEncoder;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.server.config.AppConfiguration;
import com.tuling.tim.server.handle.TIMServerHandle;
import com.tuling.tim.server.util.SpringBeanFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * @since JDK 1.8
 */
public class TIMServerInitializer extends ChannelInitializer<Channel> {

    private final TIMServerHandle timServerHandle = new TIMServerHandle();

    @Override
    protected void initChannel(Channel ch) throws Exception {
        AppConfiguration configuration = SpringBeanFactory.getBean(AppConfiguration.class);

        ch.pipeline()
                // 根据配置没有收到客户端发送消息或心跳就触发读空闲
                .addLast(new IdleStateHandler((int) configuration.getHeartBeatTime(), 0, 0))
                .addLast(new ObjEncoder(TIMReqMsg.class))
                .addLast(new ObjDecoder(TIMReqMsg.class))
                .addLast(timServerHandle);
    }
}
