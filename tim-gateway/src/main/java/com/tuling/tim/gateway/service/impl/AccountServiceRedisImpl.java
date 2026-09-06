package com.tuling.tim.gateway.service.impl;

import com.tuling.tim.common.enums.StatusEnum;
import com.tuling.tim.common.exception.TIMException;
import com.tuling.tim.common.util.RouteInfoParseUtil;
import com.tuling.tim.gateway.api.vo.req.LoginReqVO;
import com.tuling.tim.gateway.api.vo.res.RegisterInfoResVO;
import com.tuling.tim.gateway.api.vo.res.TIMServerResVO;
import com.tuling.tim.gateway.service.AccountService;
import com.tuling.tim.gateway.service.UserInfoCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.tuling.tim.common.enums.StatusEnum.OFF_LINE;
import static com.tuling.tim.gateway.constant.Constant.ACCOUNT_PREFIX;
import static com.tuling.tim.gateway.constant.Constant.ROUTE_PREFIX;

/**
 * @since JDK 1.8
 */
@Service
public class AccountServiceRedisImpl implements AccountService {
    private final static Logger LOGGER = LoggerFactory.getLogger(AccountServiceRedisImpl.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserInfoCacheService userInfoCacheService;

    @Override
    public RegisterInfoResVO register(RegisterInfoResVO info) {
        String key = ACCOUNT_PREFIX + info.getUserId();

        String name = redisTemplate.opsForValue().get(info.getUserName());
        if (null == name) {
            //为了方便查询，冗余一份
            redisTemplate.opsForValue().set(key, info.getUserName());
            redisTemplate.opsForValue().set(info.getUserName(), key);
        } else {
            long userId = Long.parseLong(name.split(":")[1]);
            info.setUserId(userId);
            info.setUserName(info.getUserName());
        }

        return info;
    }

    @Override
    public StatusEnum login(LoginReqVO loginReqVO) throws Exception {
        //再去Redis里查询
        String key = ACCOUNT_PREFIX + loginReqVO.getUserId();
        String userName = redisTemplate.opsForValue().get(key);
        if (null == userName) {
            return StatusEnum.ACCOUNT_NOT_MATCH;
        }

        if (!userName.equals(loginReqVO.getUserName())) {
            return StatusEnum.ACCOUNT_NOT_MATCH;
        }

        //登录成功，保存登录状态
        boolean status = userInfoCacheService.saveAndCheckUserLoginStatus(loginReqVO.getUserId());
        if (status == false) {
            //重复登录
            return StatusEnum.REPEAT_LOGIN;
        }

        return StatusEnum.SUCCESS;
    }

    @Override
    public Map<Long, TIMServerResVO> loadRouteRelated() {

        Map<Long, TIMServerResVO> routes = new HashMap<>(64);
        RedisConnection connection = null;
        Cursor<byte[]> scan = null;
        try {
            connection = redisTemplate.getConnectionFactory().getConnection();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(ROUTE_PREFIX + "*")
                    .build();
            scan = connection.scan(options);

            while (scan.hasNext()) {
                byte[] next = scan.next();
                String key = new String(next, StandardCharsets.UTF_8);
                LOGGER.info("key={}", key);
                parseServerInfo(routes, key);
            }
        } finally {
            if (scan != null) {
                try {
                    scan.close();
                } catch (RuntimeException e) {
                    LOGGER.error("Unable to close Redis scan cursor", e);
                }
            }
            if (connection != null) {
                connection.close();
            }
        }

        return routes;
    }

    @Override
    public TIMServerResVO loadRouteRelatedByUserId(Long userId) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        String value = hash == null ? redisTemplate.opsForValue().get(ROUTE_PREFIX + userId)
                : hash.get(ROUTE_PREFIX + userId, "route");

        if (value == null) {
            throw new TIMException(OFF_LINE);
        }

        TIMServerResVO TIMServerResVO = new TIMServerResVO(RouteInfoParseUtil.parse(value));
        return TIMServerResVO;
    }

    private void parseServerInfo(Map<Long, TIMServerResVO> routes, String key) {
        long userId = Long.valueOf(key.substring(ROUTE_PREFIX.length()));
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        String value = hash == null ? redisTemplate.opsForValue().get(key) : hash.get(key, "route");
        if (value == null) return;
        TIMServerResVO TIMServerResVO = new TIMServerResVO(RouteInfoParseUtil.parse(value));
        routes.put(userId, TIMServerResVO);
    }


    @Override
    public void offLine(Long userId) throws Exception {
        // Route ownership belongs to the authenticated Netty session.  This
        // compatibility endpoint must not delete a replacement connection's
        // route; the server closes it with sessionId + epoch matching.
        userInfoCacheService.removeLoginStatus(userId);
    }
}
