package com.tuling.tim.server.server;

import com.tuling.tim.common.constant.Constants;
import com.tuling.tim.common.enums.StatusEnum;
import com.tuling.tim.common.protocol.TIMReqMsg;
import com.tuling.tim.common.res.BaseResponse;
import com.tuling.tim.server.api.vo.req.SendMsgReqVO;
import com.tuling.tim.server.api.vo.res.SendMsgResVO;
import com.tuling.tim.server.init.TIMServerInitializer;
import com.tuling.tim.server.util.SessionSocketHolder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;

/**
 * @since JDK 1.8
 */
@Component
public class TIMServer {

    private final static Logger LOGGER = LoggerFactory.getLogger(TIMServer.class);

    private EventLoopGroup boss = new NioEventLoopGroup();
    private EventLoopGroup work = new NioEventLoopGroup();


    @Value("${tim.server.port}")
    private int nettyPort;


    /**
     * 启动 tim server
     *
     * @return
     * @throws InterruptedException
     */
    @PostConstruct
    public void start() throws InterruptedException {

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(boss, work)
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(nettyPort))
                //保持长连接
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new TIMServerInitializer());

        ChannelFuture future = bootstrap.bind().sync();
        if (future.isSuccess()) {
            LOGGER.info("Start tim server success!!!");
        }
    }


    /**
     * 销毁
     */
    @PreDestroy
    public void destroy() {
        boss.shutdownGracefully().syncUninterruptibly();
        work.shutdownGracefully().syncUninterruptibly();
        LOGGER.info("Close tim server success!!!");
    }


    /**
     * Push msg to client.
     *
     * @param sendMsgReqVO 消息
     */
    public BaseResponse<SendMsgResVO> sendMsg(SendMsgReqVO sendMsgReqVO) {
        NioSocketChannel socketChannel = SessionSocketHolder.get(sendMsgReqVO.getUserId());

        if (null == socketChannel) {
            LOGGER.error("client {} offline!", sendMsgReqVO.getUserId());
            BaseResponse<SendMsgResVO> res = new BaseResponse<>();
            res.setCode(StatusEnum.OFF_LINE.getCode());
            res.setMessage(StatusEnum.OFF_LINE.getMessage());
            return res;
        }
        TIMReqMsg protocol = new TIMReqMsg(sendMsgReqVO.getUserId(), sendMsgReqVO.getMsg(), Constants.CommandType.MSG);

        ChannelFuture future = socketChannel.writeAndFlush(protocol);
        if (future == null) {
            BaseResponse<SendMsgResVO> res = new BaseResponse<>();
            res.setCode(StatusEnum.FAIL.getCode());
            res.setMessage(StatusEnum.FAIL.getMessage());
            return res;
        }
        future.addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                LOGGER.info("server push msg:[{}]", sendMsgReqVO.toString());
            } else {
                LOGGER.error("server push msg failed:[{}]", sendMsgReqVO.toString(), channelFuture.cause());
            }
        });
        BaseResponse<SendMsgResVO> res = new BaseResponse<>();
        SendMsgResVO sendMsgResVO = new SendMsgResVO();
        sendMsgResVO.setMsg("OK");
        res.setCode(StatusEnum.SUCCESS.getCode());
        res.setMessage(StatusEnum.SUCCESS.getMessage());
        res.setDataBody(sendMsgResVO);
        return res;
    }
}
