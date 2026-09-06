package com.tuling.tim.gateway.controller;

import com.tuling.tim.common.enums.StatusEnum;
import com.tuling.tim.common.exception.TIMException;
import com.tuling.tim.common.pojo.RouteInfo;
import com.tuling.tim.common.pojo.TIMUserInfo;
import com.tuling.tim.common.res.BaseResponse;
import com.tuling.tim.common.res.NULLBody;
import com.tuling.tim.common.route.algorithm.RouteHandle;
import com.tuling.tim.common.util.RouteInfoParseUtil;
import com.tuling.tim.gateway.api.RouteApi;
import com.tuling.tim.gateway.api.vo.req.ChatReqVO;
import com.tuling.tim.gateway.api.vo.req.LoginReqVO;
import com.tuling.tim.gateway.api.vo.req.P2PReqVO;
import com.tuling.tim.gateway.api.vo.req.RegisterInfoReqVO;
import com.tuling.tim.gateway.api.vo.res.RegisterInfoResVO;
import com.tuling.tim.gateway.api.vo.res.TIMServerResVO;
import com.tuling.tim.gateway.cache.ServerCache;
import com.tuling.tim.gateway.service.AccountService;
import com.tuling.tim.gateway.service.CommonBizService;
import com.tuling.tim.gateway.service.UserInfoCacheService;
import com.tuling.tim.common.security.ConnectToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.Valid;

/**
 * @since JDK 1.8
 */
@Controller
@Validated
@RequestMapping("/")
public class RouteController implements RouteApi {
    private final static Logger LOGGER = LoggerFactory.getLogger(RouteController.class);

    @Autowired
    private ServerCache serverCache;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserInfoCacheService userInfoCacheService;

    @Autowired
    private CommonBizService commonBizService;

    @Autowired
    private RouteHandle routeHandle;

    @Value("${tim.connect-token.secret:}")
    private String connectTokenSecret;
    @Value("${tim.connect-token.ttl-ms:60000}")
    private long connectTokenTtlMs;

    /**
     * 群聊 API
     *
     * @param groupReqVO
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "groupRoute", method = RequestMethod.POST)
    @ResponseBody()
    @Override
    public BaseResponse<NULLBody> groupRoute(@Valid @RequestBody ChatReqVO groupReqVO) throws Exception {
        BaseResponse<NULLBody> res = new BaseResponse();
        res.setCode(StatusEnum.FAIL.getCode());
        res.setMessage("Deprecated: send GROUP_CHAT through the authenticated Netty connection");
        return res;
    }


    /**
     * 私聊 API
     *
     * @param p2pRequest
     * @return
     */
    @RequestMapping(value = "p2pRoute", method = RequestMethod.POST)
    @ResponseBody()
    @Override
    public BaseResponse<NULLBody> p2pRoute(@Valid @RequestBody P2PReqVO p2pRequest) throws Exception {
        BaseResponse<NULLBody> res = new BaseResponse();
        res.setCode(StatusEnum.FAIL.getCode());
        res.setMessage("Deprecated: send CHAT through the authenticated Netty connection");
        return res;
    }

    /**
     * 客户端下线
     *
     * @param groupReqVO
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "offLine", method = RequestMethod.POST)
    @ResponseBody()
    @Override
    public BaseResponse<NULLBody> offLine(@Valid @RequestBody ChatReqVO groupReqVO) throws Exception {
        BaseResponse<NULLBody> res = new BaseResponse();

        TIMUserInfo timUserInfo = userInfoCacheService.loadUserInfoByUserId(groupReqVO.getUserId());

        LOGGER.info("user [{}] offline!", timUserInfo.toString());
        accountService.offLine(groupReqVO.getUserId());

        res.setCode(StatusEnum.SUCCESS.getCode());
        res.setMessage(StatusEnum.SUCCESS.getMessage());
        return res;
    }

    /**
     * 登录并获取一台 TIM server
     *
     * @return
     */
    @RequestMapping(value = "login", method = RequestMethod.POST)
    @ResponseBody()
    @Override
    public BaseResponse<TIMServerResVO> login(@Valid @RequestBody LoginReqVO loginReqVO) throws Exception {
        BaseResponse<TIMServerResVO> res = new BaseResponse();

        //登录校验
        StatusEnum status = accountService.login(loginReqVO);
        if (status == StatusEnum.SUCCESS) {

            // 从zookeeper里挑选一台客户端需访问的netty服务器
            String server = routeHandle.routeServer(serverCache.getServerList(), String.valueOf(loginReqVO.getUserId()));
            LOGGER.info("userName=[{}] route server info=[{}]", loginReqVO.getUserName(), server);

            // check server available
            RouteInfo routeInfo = RouteInfoParseUtil.parse(server);
            commonBizService.checkServerAvailable(routeInfo);

            TIMServerResVO vo = new TIMServerResVO(routeInfo);
            String serverId = serverCache.serverIdForRoute(server);
            vo.setServerId(serverId);
            vo.setConnectToken(ConnectToken.issue(loginReqVO.getUserId(), serverId, connectTokenTtlMs, connectTokenSecret));
            res.setDataBody(vo);

        }
        res.setCode(status.getCode());
        res.setMessage(status.getMessage());

        return res;
    }

    /**
     * 注册账号
     *
     * @return
     */
    @RequestMapping(value = "registerAccount", method = RequestMethod.POST)
    @ResponseBody()
    @Override
    public BaseResponse<RegisterInfoResVO> registerAccount(@Valid @RequestBody RegisterInfoReqVO registerInfoReqVO) throws Exception {
        BaseResponse<RegisterInfoResVO> res = new BaseResponse();

        long userId = System.currentTimeMillis();
        RegisterInfoResVO info = new RegisterInfoResVO(userId, registerInfoReqVO.getUserName());
        info = accountService.register(info);

        res.setDataBody(info);
        res.setCode(StatusEnum.SUCCESS.getCode());
        res.setMessage(StatusEnum.SUCCESS.getMessage());
        return res;
    }

    /**
     * 获取所有在线用户
     *
     * @return
     */
    @RequestMapping(value = "onlineUser", method = RequestMethod.POST)
    @ResponseBody()
    @Override
    public BaseResponse<Set<TIMUserInfo>> onlineUser() throws Exception {
        BaseResponse<Set<TIMUserInfo>> res = new BaseResponse();

        Set<TIMUserInfo> timUserInfos = userInfoCacheService.onlineUser();
        if (timUserInfos == null) {
            timUserInfos = java.util.Collections.emptySet();
        }
        res.setDataBody(timUserInfos);
        res.setCode(StatusEnum.SUCCESS.getCode());
        res.setMessage(StatusEnum.SUCCESS.getMessage());
        return res;
    }


}
