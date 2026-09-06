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
import com.tuling.tim.common.security.ConnectToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * @since JDK 1.8
 */
@ChannelHandler.Sharable
public class TIMServerHandle extends SimpleChannelInboundHandler<TIMReqMsg> {

    private final static Logger LOGGER = LoggerFactory.getLogger(TIMReqMsg.class);

    @Value("${tim.message.max-content-length:65536}")
    private int maxContentLength;
    @Value("${tim.server.id:im-server-1}")
    private String serverId;
    @Value("${tim.connect-token.secret:}")
    private String connectTokenSecret;


    /**
     * 取消绑定
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //可能出现业务判断离线后再次触发 channelInactive
        TIMUserInfo userInfo = SessionSocketHolder.getUserId(ctx.channel());
        if (userInfo != null) {
            LOGGER.warn("[{}] trigger channelInactive offline!", userInfo.getUserName());

            //Clear route info and offline.
            RouteHandler routeHandler = SpringBeanFactory.getBean(RouteHandler.class);
            routeHandler.userOffLine(userInfo, ctx.channel());

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
            ObjectMapper mapper = SpringBeanFactory.getBean(ObjectMapper.class);
            com.fasterxml.jackson.databind.JsonNode loginNode = mapper.readTree(msg.getReqMsg());
            String userName = loginNode.path("userName").asText(msg.getReqMsg());
            long offlineCursor = loginNode.path("offlineCursor").asLong(0L);
            ConnectToken.Claims claims;
            try {
                claims = ConnectToken.verify(loginNode.path("connectToken").asText(null), serverId, connectTokenSecret, System.currentTimeMillis());
            } catch (RuntimeException invalidToken) {
                LOGGER.warn("rejecting invalid Netty login: {}", invalidToken.getMessage());
                ctx.close();
                return;
            }
            long userId = claims.userId();
            //保存客户端与 Channel 之间的关系
            String[] session = SessionSocketHolder.put(userId, ctx.channel()).split(":", 2);
            SessionSocketHolder.saveSession(userId, userName);
            SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() ->
                    { SpringBeanFactory.getBean(RedisRouteService.class).online(userId, session[0], Long.parseLong(session[1]));
                      SpringBeanFactory.getBean(GroupMessageService.class).restoreLocalMembership(userId, ctx.channel());
                      SpringBeanFactory.getBean(ReliableMessageService.class).replayOffline(userId, offlineCursor, ctx.channel()); });
            LOGGER.info("client [{}] online success!!", userId);
        }

        //心跳更新时间
        if (msg.getType() == Constants.CommandType.PING) {
            NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
            ConnectionSession session = SessionSocketHolder.getSession(ctx.channel());
            if (session != null && isAuthenticated(session, ctx.channel())) {
                SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() -> {
                    boolean renewed = SpringBeanFactory.getBean(RedisRouteService.class)
                            .renew(session.getUserId(), session.getSessionId(), session.getEpoch());
                    if (!renewed) LOGGER.warn("route renewal rejected for user={}, session={}", session.getUserId(), session.getSessionId());
                });
            }
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

        if (msg.getType() == Constants.CommandType.CHAT || msg.getType() == Constants.CommandType.GROUP_CHAT) {
            ConnectionSession session = SessionSocketHolder.getSession(ctx.channel());
            if (!isAuthenticated(session, ctx.channel())) { ctx.close(); return; }
            ChatMessage chat = SpringBeanFactory.getBean(ObjectMapper.class).readValue(msg.getReqMsg(), ChatMessage.class);
            if (!contentWithinLimit(chat, maxContentLength)) { ctx.close(); return; }
            trustSessionIdentity(chat, session);
            SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() -> {
                if (msg.getType() == Constants.CommandType.GROUP_CHAT) {
                    SpringBeanFactory.getBean(com.tuling.tim.server.group.GroupMessageService.class).send(chat);
                } else {
                    SpringBeanFactory.getBean(ReliableMessageService.class).accept(chat);
                }
            });
        }

        if (msg.getType() == Constants.CommandType.ACK) {
            ConnectionSession session = SessionSocketHolder.getSession(ctx.channel());
            SpringBeanFactory.getBean(ThreadPoolExecutor.class).execute(() -> {
                if (session != null && msg.getReqMsg() != null && msg.getReqMsg().startsWith("OFFLINE:")) {
                    SpringBeanFactory.getBean(ReliableMessageService.class).acknowledgeOffline(session.getUserId(), Long.parseLong(msg.getReqMsg().substring("OFFLINE:".length())));
                } else {
                    SpringBeanFactory.getBean(ReliableMessageService.class).acknowledge(msg.getReqMsg());
                }
            });
        }

    }

    static void trustSessionIdentity(ChatMessage message, ConnectionSession session) {
        message.setFromUserId(session.getUserId());
    }

    static boolean isAuthenticated(ConnectionSession session, io.netty.channel.Channel channel) {
        return session != null && SessionSocketHolder.isCurrent(session.getUserId(), channel);
    }

    static boolean contentWithinLimit(ChatMessage message, int maxLength) {
        return message != null && maxLength > 0 && message.getContent() != null
                && message.getContent().length() <= maxLength;
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (TIMException.isResetByPeer(cause.getMessage())) {
            return;
        }

        LOGGER.error(cause.getMessage(), cause);

    }

}
