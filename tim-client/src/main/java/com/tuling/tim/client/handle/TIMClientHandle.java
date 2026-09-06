package com.tuling.tim.client.handle;

import com.tuling.tim.client.service.EchoService;
import com.tuling.tim.client.service.ReConnectManager;
import com.tuling.tim.client.service.OfflineCursorStore;
import com.tuling.tim.client.service.MessageDeduplicator;
import com.tuling.tim.client.service.ShutDownMsg;
import com.tuling.tim.client.service.impl.EchoServiceImpl;
import com.tuling.tim.client.util.SpringBeanFactory;
import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.common.util.NettyAttrUtil;
import com.vdurmont.emoji.EmojiParser;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since JDK 1.8
 */
public class TIMClientHandle extends SimpleChannelInboundHandler<TIMReqMsg> {

    private final static Logger LOGGER = LoggerFactory.getLogger(TIMClientHandle.class);

    private MsgHandleCaller caller;

    private ThreadPoolExecutor threadPoolExecutor;

    private ScheduledExecutorService scheduledExecutorService;

    private ReConnectManager reConnectManager;

    private ShutDownMsg shutDownMsg;

    private EchoService echoService;

    private OfflineCursorStore offlineCursorStore;

    private MessageDeduplicator deduplicator;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;

            if (idleStateEvent.state() == IdleState.WRITER_IDLE) {
                TIMReqMsg heartBeat = SpringBeanFactory.getBean("heartBeat", TIMReqMsg.class);
                //System.out.println("客户端给服务端发送心跳");
                ctx.writeAndFlush(heartBeat).addListeners((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        LOGGER.error("IO error,close Channel");
                        future.channel().close();
                    }
                });
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //客户端和服务端建立连接时调用
        LOGGER.info("tim server connect success!");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        if (shutDownMsg == null) {
            shutDownMsg = SpringBeanFactory.getBean(ShutDownMsg.class);
        }

        //用户主动退出，不执行重连逻辑
        if (shutDownMsg.checkStatus()) {
            return;
        }

        if (scheduledExecutorService == null) {
            scheduledExecutorService = SpringBeanFactory.getBean("scheduledTask", ScheduledExecutorService.class);
            reConnectManager = SpringBeanFactory.getBean(ReConnectManager.class);
        }
        LOGGER.info("客户端断开了，重新连接！");
        reConnectManager.reConnect(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TIMReqMsg msg) throws Exception {
        if (echoService == null) {
            echoService = SpringBeanFactory.getBean(EchoServiceImpl.class);
        }


        //心跳更新时间
        if (msg.getType() == Constants.CommandType.PING) {
            //LOGGER.info("收到服务端心跳！！！");
            NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
        }

        if (msg.getType() != Constants.CommandType.PING) {
            if (deduplicator == null) deduplicator = SpringBeanFactory.getBean(MessageDeduplicator.class);
            String messageId = messageId(msg.getReqMsg());
            if (!deduplicator.firstDelivery(messageId)) {
                if (msg.getType() == Constants.CommandType.CHAT) acknowledgeAfterHandling(ctx, msg.getReqMsg());
                return;
            }
            callBackMsg(msg.getReqMsg(), () -> {
                // Echo and ACK only after the application callback succeeds.
                echoService.echo(EmojiParser.parseToUnicode(msg.getReqMsg()));
                if (msg.getType() == Constants.CommandType.CHAT) acknowledgeAfterHandling(ctx, msg.getReqMsg());
            });
        }

    }

    private String messageId(String payload) {
        try {
            JsonNode node = new ObjectMapper().readTree(payload);
            String id = node.path("messageId").asText();
            if (!id.isBlank()) return id;
            return node.path("clientMessageId").asText();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 回调消息
     *
     * @param msg
     */
    private void callBackMsg(String msg, Runnable onSuccess) {
        threadPoolExecutor = SpringBeanFactory.getBean("callBackThreadPool", ThreadPoolExecutor.class);
        threadPoolExecutor.execute(() -> {
            try {
                caller = SpringBeanFactory.getBean(MsgHandleCaller.class);
                caller.getMsgHandleListener().handle(msg);
                onSuccess.run();
            } catch (Exception e) {
                LOGGER.warn("message handler failed; ACK withheld", e);
            }
        });

    }

    private void acknowledgeAfterHandling(ChannelHandlerContext ctx, String payload) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String messageId = node.path("messageId").asText();
            if (!messageId.isEmpty()) ctx.writeAndFlush(new TIMReqMsg(0L, messageId, Constants.CommandType.ACK));
            long deliveryCursor = node.path("deliveryCursor").asLong(0L);
            if (deliveryCursor > 0L) {
                if (offlineCursorStore == null) offlineCursorStore = SpringBeanFactory.getBean(OfflineCursorStore.class);
                offlineCursorStore.advance(deliveryCursor);
                ctx.writeAndFlush(new TIMReqMsg(0L, "OFFLINE:" + offlineCursorStore.current(), Constants.CommandType.ACK));
            }
        } catch (Exception e) {
            LOGGER.warn("cannot ACK malformed chat payload", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //异常时断开连接
        cause.printStackTrace();
        ctx.close();
    }
}
