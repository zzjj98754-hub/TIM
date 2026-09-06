package com.tuling.tim.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.common.enums.StatusEnum;
import com.tuling.tim.common.exception.TIMException;
import com.tuling.tim.common.pojo.TIMUserInfo;
import com.tuling.tim.common.res.BaseResponse;
import com.tuling.tim.common.util.JsonHttpClient;
import com.tuling.tim.common.util.RouteInfoParseUtil;
import com.tuling.tim.gateway.api.vo.req.ChatReqVO;
import com.tuling.tim.gateway.api.vo.req.LoginReqVO;
import com.tuling.tim.gateway.api.vo.res.RegisterInfoResVO;
import com.tuling.tim.gateway.api.vo.res.TIMServerResVO;
import com.tuling.tim.gateway.service.AccountService;
import com.tuling.tim.gateway.service.UserInfoCacheService;
import com.tuling.tim.server.api.vo.req.SendMsgReqVO;
import okhttp3.OkHttpClient;
import okhttp3.Response;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserInfoCacheService userInfoCacheService;

    @Autowired
    private OkHttpClient okHttpClient;

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
    public void saveRouteInfo(LoginReqVO loginReqVO, String msg) throws Exception {
        String key = ROUTE_PREFIX + loginReqVO.getUserId();
        Map<String, String> route = new HashMap<>();
        route.put("route", msg); route.put("nodeId", "pending");
        route.put("sessionId", "gateway-login"); route.put("epoch", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(key, route);
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
    public void pushMsg(TIMServerResVO TIMServerResVO, long sendUserId, ChatReqVO groupReqVO) throws Exception {
        TIMUserInfo timUserInfo = userInfoCacheService.loadUserInfoByUserId(sendUserId);

        String url = "http://" + TIMServerResVO.getIp() + ":" + TIMServerResVO.getHttpPort();
        SendMsgReqVO vo = new SendMsgReqVO(timUserInfo.getUserName() + ":" + groupReqVO.getMsg(), groupReqVO.getUserId());
        Response response = null;
        try {
            response = JsonHttpClient.post(okHttpClient, url, "/sendMsg", vo);
            if (response == null) {
                throw new TIMException(StatusEnum.FAIL);
            }
            if (!response.isSuccessful()) {
                throw new TIMException(StatusEnum.FAIL);
            }
            BaseResponse result = OBJECT_MAPPER.readValue(response.body().string(), BaseResponse.class);
            if (result == null || !StatusEnum.SUCCESS.getCode().equals(result.getCode())) {
                throw new TIMException(StatusEnum.FAIL);
            }
        } catch (Exception e) {
            LOGGER.error("Exception", e);
            throw e;
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
    }

    @Override
    public void offLine(Long userId) throws Exception {

        // TODO 这里需要用lua保证原子性

        //删除路由
        redisTemplate.delete(ROUTE_PREFIX + userId);

        //删除登录状态
        userInfoCacheService.removeLoginStatus(userId);
    }
}
