package com.tuling.tim.client.thread;

import com.tuling.tim.client.service.impl.ClientHeartBeatHandlerImpl;
import com.tuling.tim.client.util.SpringBeanFactory;
import com.tuling.tim.common.kit.HeartBeatHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tuling.tim.client.service.ReConnectManager;

/**
 *
 * @since JDK 1.8
 */
public class ReConnectJob implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(ReConnectJob.class);

    private ChannelHandlerContext context ;

    private HeartBeatHandler heartBeatHandler ;
    private final ReConnectManager manager;

    public ReConnectJob(ChannelHandlerContext context) {
        this(context, null);
    }

    public ReConnectJob(ChannelHandlerContext context, ReConnectManager manager) {
        this.context = context;
        this.manager = manager;
        this.heartBeatHandler = SpringBeanFactory.getBean(ClientHeartBeatHandlerImpl.class) ;
    }

    @Override
    public void run() {
        try {
            heartBeatHandler.process(context);
        } catch (Exception e) {
            LOGGER.error("Exception",e);
            if (manager != null) manager.reConnect(context);
        }
    }
}
