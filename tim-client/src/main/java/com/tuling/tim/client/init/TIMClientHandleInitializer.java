package com.tuling.tim.client.init;

import com.tuling.tim.client.handle.TIMClientHandle;
import com.tuling.tim.client.util.SpringBeanFactory;
import com.tuling.tim.client.config.AppConfiguration;
import com.tuling.tim.common.protocol.ObjDecoder;
import com.tuling.tim.common.protocol.ObjEncoder;
import com.tuling.tim.common.protocol.TIMReqMsg;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * @since JDK 1.8
 */
public class TIMClientHandleInitializer extends ChannelInitializer<Channel> {

    private final TIMClientHandle TIMClientHandle = new TIMClientHandle();

    @Override
    protected void initChannel(Channel ch) throws Exception {
        AppConfiguration appConfiguration = SpringBeanFactory.getBean(AppConfiguration.class);
        ch.pipeline()
                // 根据配置触发写空闲，执行 TIMClientHandle 的心跳逻辑
                .addLast(new IdleStateHandler(0, (int) appConfiguration.getHeartBeatTime(), 0))
                .addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 14, 4, 0, 0))
                .addLast(new ObjEncoder(TIMReqMsg.class))
                .addLast(new ObjDecoder(TIMReqMsg.class))
                .addLast(TIMClientHandle)
        ;
    }
}
