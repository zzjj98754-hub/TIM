package com.tuling.tim.server.kit;

import com.tuling.tim.common.pojo.TIMUserInfo;
import com.tuling.tim.server.util.SessionSocketHolder;
import com.tuling.tim.server.route.RedisRouteService;
import com.tuling.tim.server.util.SpringBeanFactory;
import com.tuling.tim.server.util.ConnectionSession;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @since JDK 1.8
 */
@Component
public class RouteHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(RouteHandler.class);

    /**
     * 用户下线
     *
     * @param userInfo
     * @param channel
     * @throws IOException
     */
    public void userOffLine(TIMUserInfo userInfo, Channel channel) {
        if (userInfo != null) {
            LOGGER.info("Account [{}] offline", userInfo.getUserName());
            ConnectionSession session = SessionSocketHolder.getSession(channel);
            if (SessionSocketHolder.isCurrent(userInfo.getUserId(), channel)) {
                SessionSocketHolder.removeSession(userInfo.getUserId());
            }
            if (session != null) SpringBeanFactory.getBean(RedisRouteService.class)
                    .offline(userInfo.getUserId(), session.getSessionId(), session.getEpoch());
        }
        SessionSocketHolder.remove(channel);

    }
}
