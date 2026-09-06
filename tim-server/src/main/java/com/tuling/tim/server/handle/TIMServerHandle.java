package com.tuling.tim.server.handle;

import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.exception.TIMException;
import com.tuling.tim.common.kit.HeartBeatHandler;
import com.tuling.tim.common.pojo.TIMUserInfo;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.common.util.NettyAttrUtil;
import com.tuling.tim.server.kit.RouteHandler;
import com.tuling.tim.server.kit.ServerHeartBeatHandlerImpl;
import com.tuling.tim.server.util.SessionSocketHolder;
import com.tuling.tim.server.util.SpringBeanFactory;
import com.tuling.tim.server.util.ConnectionSession;
import com.tuling.tim.server.route.RedisRouteService;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.message.ReliableMessageService;
import com.tuling.tim.server.group.GroupMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @since JDK 1.8
 */
@ChannelHandler.Sharable
public class TIMServerHandle extends SimpleChannelInboundHandler<TIMReqMsg> {

    private final static Logger LOGGER = LoggerFactory.getLogger(TIMReqMsg.class);


    /**
     * 取消绑定
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //可能出现业务判断离线后再次触发 channelInactive
        TIMUserInfo userInfo = SessionSocketHolder.getUserId((NioSocketChannel) ctx.channel());
        if (userInfo != null) {
            LOGGER.warn("[{}] trigger channelInactive offline!", userInfo.getUserName());

            //Clear route info and offline.
            RouteHandler routeHandler = SpringBeanFactory.getBean(RouteHandler.class);
            routeHandler.userOffLine(userInfo, (NioSocketChannel) ctx.channel());

            ctx.channel().close();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                //TIMUserInfo userInfo = SessionSocketHolder.getUserId((NioSocketChannel) ctx.channel());
                //System.out.println("定时检测客户端是否存活:" + userInfo.getUserName());
                HeartBeatHandler heartBeatHandler = SpringBeanFactory.getBean(ServerHeartBeatHandlerImpl.class);
                heartBeatHandler.process(ctx);
            }
        }
        super.userEventTriggered(ctx, evt);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TIMReqMsg msg) throws Exception {
        LOGGER.info("received msg=[{}]", msg.toString());

        if (msg.getType() == Constants.CommandType.LOGIN) {
            //保存客户端与 Channel 之间的关系
            String[] session = SessionSocketHolder.put(msg.getRequestId(), (NioSocketChannel) ctx.channel()).split(":", 2);
            SessionSocketHolder.saveSession(msg.getRequestId(), msg.getReqMsg());
            SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() ->
                    { SpringBeanFactory.getBean(RedisRouteService.class).online(msg.getRequestId(), session[0], Long.parseLong(session[1]));
                      SpringBeanFactory.getBean(GroupMessageService.class).restoreLocalMembership(msg.getRequestId(), (NioSocketChannel) ctx.channel()); });
            LOGGER.info("client [{}] online success!!", msg.getReqMsg());
        }

        //心跳更新时间
        if (msg.getType() == Constants.CommandType.PING) {
            NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
            //向客户端响应 pong 消息
            TIMReqMsg heartBeat = SpringBeanFactory.getBean("heartBeat",
                    TIMReqMsg.class);
            ctx.writeAndFlush(heartBeat).addListeners((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    LOGGER.error("IO error,close Channel");
                    future.channel().close();
                }
            });
        }

        if (msg.getType() == Constants.CommandType.CHAT) {
            ConnectionSession session = SessionSocketHolder.getSession((NioSocketChannel) ctx.channel());
            if (session == null) { ctx.close(); return; }
            ChatMessage chat = SpringBeanFactory.getBean(ObjectMapper.class).readValue(msg.getReqMsg(), ChatMessage.class);
            chat.setFromUserId(session.getUserId());
            SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() ->
                    SpringBeanFactory.getBean(ReliableMessageService.class).accept(chat));
        }

        if (msg.getType() == Constants.CommandType.ACK) {
            SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() ->
                    SpringBeanFactory.getBean(ReliableMessageService.class).acknowledge(msg.getReqMsg()));
        }

    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (TIMException.isResetByPeer(cause.getMessage())) {
            return;
        }

        LOGGER.error(cause.getMessage(), cause);

    }

}
