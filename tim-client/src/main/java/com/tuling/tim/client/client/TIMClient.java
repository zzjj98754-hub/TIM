package com.tuling.tim.client.client;

import com.tuling.tim.client.config.AppConfiguration;
import com.tuling.tim.client.init.TIMClientHandleInitializer;
import com.tuling.tim.client.service.EchoService;
import com.tuling.tim.client.service.MsgHandle;
import com.tuling.tim.client.service.ReConnectManager;
import com.tuling.tim.client.service.RouteRequest;
import com.tuling.tim.client.service.impl.ClientInfo;
import com.tuling.tim.client.thread.ContextHolder;
import com.tuling.tim.client.vo.req.GoogleProtocolVO;
import com.tuling.tim.client.vo.req.LoginReqVO;
import com.tuling.tim.client.vo.res.TIMServerResVO;
import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.protocol.TIMReqMsg;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * @since JDK 1.8
 */
@Component
public class TIMClient {

    private final static Logger LOGGER = LoggerFactory.getLogger(TIMClient.class);

    private EventLoopGroup group = new NioEventLoopGroup(1, new DefaultThreadFactory("tim-work"));

    @Value("${tim.user.id:0}")
    private long userId;

    @Value("${tim.user.userName:test}")
    private String userName;

    private SocketChannel channel;

    @Autowired
    private EchoService echoService;

    @Autowired
    private RouteRequest routeRequest;

    @Autowired
    private AppConfiguration configuration;

    @Autowired
    private ObjectProvider<MsgHandle> msgHandleProvider;

    @Autowired
    private ClientInfo clientInfo;

    @Autowired
    private ReConnectManager reConnectManager;

    @Value("${tim.client.auto-start:true}")
    private boolean autoStart;

    /**
     * 重试次数
     */
    private int errorCount;

    @PostConstruct
    public void start() throws Exception {
        if (!autoStart) {
            LOGGER.info("tim client auto-start disabled, skip bootstrapping");
            return;
        }

        //登录 + 获取可以使用的服务器 ip+port
        TIMServerResVO.ServerInfo timServer = userLogin();

        //启动客户端
        startClient(timServer);

        //向服务端注册
        loginTIMServer();
    }

    /**
     * 启动客户端
     *
     * @param timServer
     * @throws Exception
     */
    private void startClient(TIMServerResVO.ServerInfo timServer) {
        if (timServer == null) {
            throw new IllegalStateException("timServer is null");
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new TIMClientHandleInitializer());

        ChannelFuture future = null;
        try {
            future = bootstrap.connect(timServer.getIp(), timServer.getTimServerPort()).sync();
        } catch (Exception e) {
            errorCount++;

            if (errorCount >= configuration.getErrorCount()) {
                LOGGER.error("连接失败次数达到上限[{}]次", errorCount);
                msgHandleProvider.getObject().shutdown();
            }
            LOGGER.error("Connect fail!", e);
            throw new IllegalStateException("Failed to connect to tim server", e);
        }
        if (future == null) {
            throw new IllegalStateException("Connect future is null");
        }
        if (future.isSuccess()) {
            echoService.echo("Start tim client success!");
            LOGGER.info("启动 tim client 成功");
        } else {
            throw new IllegalStateException("Failed to connect to tim server");
        }
        channel = (SocketChannel) future.channel();
    }

    /**
     * 登录+路由服务器
     *
     * @return 路由服务器信息
     * @throws Exception
     */
    private TIMServerResVO.ServerInfo userLogin() {
        LoginReqVO loginReqVO = new LoginReqVO(userId, userName);
        TIMServerResVO.ServerInfo timServer = null;
        try {
            timServer = routeRequest.getTIMServer(loginReqVO);
            if (timServer == null) {
                throw new IllegalStateException("timServer is null after login");
            }

            //保存系统信息
            clientInfo.saveServiceInfo(timServer.getIp() + ":" + timServer.getTimServerPort())
                    .saveUserInfo(userId, userName);

            LOGGER.info("timServer=[{}]", timServer.toString());
        } catch (Exception e) {
            errorCount++;

            if (errorCount >= configuration.getErrorCount()) {
                echoService.echo("The maximum number of reconnections has been reached[{}]times, close tim client!", errorCount);
                msgHandleProvider.getObject().shutdown();
            }
            LOGGER.error("login fail", e);
            throw new IllegalStateException("login gateway failed", e);
        }
        return timServer;
    }

    /**
     * 向服务器注册
     */
    private void loginTIMServer() {
        TIMReqMsg login = new TIMReqMsg(userId, userName, Constants.CommandType.LOGIN);
        ChannelFuture future = channel.writeAndFlush(login);
        future.addListener((ChannelFutureListener) channelFuture ->
                echoService.echo("Registry tim server success!")
        );
    }

    /**
     * 发送消息字符串
     *
     * @param msg
     */
    public void sendStringMsg(String msg) {
        ByteBuf message = Unpooled.buffer(msg.getBytes().length);
        message.writeBytes(msg.getBytes());
        ChannelFuture future = channel.writeAndFlush(message);
        future.addListener((ChannelFutureListener) channelFuture ->
                LOGGER.info("客户端手动发消息成功={}", msg));

    }

    /**
     * 发送 Google Protocol 编解码字符串
     *
     * @param googleProtocolVO
     */
    public void sendGoogleProtocolMsg(GoogleProtocolVO googleProtocolVO) {

        TIMReqMsg protocol = new TIMReqMsg(googleProtocolVO.getRequestId(), googleProtocolVO.getMsg(), Constants.CommandType.MSG);
        ChannelFuture future = channel.writeAndFlush(protocol);
        future.addListener((ChannelFutureListener) channelFuture ->
                LOGGER.info("客户端手动发送 Google Protocol 成功={}", googleProtocolVO.toString()));

    }

    /**
     * Minimal direct-message entry point for a command-line/demo caller.  The
     * receiving {@code TIMClientHandle} sends the matching ACK automatically.
     */
    public void sendChat(long toUserId, String content) {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("TIM client is not connected");
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("fromUserId", userId);
            payload.put("toUserId", toUserId);
            payload.put("content", content);
            channel.writeAndFlush(new TIMReqMsg(System.currentTimeMillis(), mapper.writeValueAsString(payload), Constants.CommandType.CHAT));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("cannot encode chat payload", e);
        }
    }

    public void sendGroupChat(long groupId, String content) {
        if (channel == null || !channel.isActive()) throw new IllegalStateException("TIM client is not connected");
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("fromUserId", userId);
            payload.put("groupId", groupId);
            payload.put("content", content);
            channel.writeAndFlush(new TIMReqMsg(System.currentTimeMillis(), mapper.writeValueAsString(payload), Constants.CommandType.GROUP_CHAT));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("cannot encode group chat payload", e);
        }
    }


    /**
     * 1. clear route information.
     * 2. reconnect.
     * 3. shutdown reconnect job.
     * 4. reset reconnect state.
     *
     * @throws Exception
     */
    public void reconnect() throws Exception {
        if (channel != null && channel.isActive()) {
            return;
        }
        //首先清除路由信息，下线
        routeRequest.offLine();

        echoService.echo("tim server shutdown, reconnecting....");
        start();
        echoService.echo("Great! reConnect success!!!");
        reConnectManager.reConnectSuccess();
        ContextHolder.clear();
    }

    /**
     * 关闭
     *
     * @throws InterruptedException
     */
    public void close() throws InterruptedException {
        if (channel != null) {
            channel.close();
        }
    }
}
