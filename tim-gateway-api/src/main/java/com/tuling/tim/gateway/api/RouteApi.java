package com.tuling.tim.gateway.api;

import com.tuling.tim.common.res.BaseResponse;
import com.tuling.tim.gateway.api.vo.req.ChatReqVO;
import com.tuling.tim.gateway.api.vo.req.LoginReqVO;
import com.tuling.tim.gateway.api.vo.req.P2PReqVO;
import com.tuling.tim.gateway.api.vo.req.RegisterInfoReqVO;
import com.tuling.tim.gateway.api.vo.res.RegisterInfoResVO;
import jakarta.validation.Valid;

/**
 * Route Api
 *
 * @since JDK 1.8
 */
public interface RouteApi {

    /**
     * group chat
     *
     * @param groupReqVO
     * @return
     * @throws Exception
     */
    Object groupRoute(@Valid ChatReqVO groupReqVO) throws Exception;

    /**
     * Point to point chat
     *
     * @param p2pRequest
     * @return
     * @throws Exception
     */
    Object p2pRoute(@Valid P2PReqVO p2pRequest) throws Exception;


    /**
     * Offline account
     *
     * @param groupReqVO
     * @return
     * @throws Exception
     */
    Object offLine(@Valid ChatReqVO groupReqVO) throws Exception;

    /**
     * Login account
     *
     * @param loginReqVO
     * @return
     * @throws Exception
     */
    Object login(@Valid LoginReqVO loginReqVO) throws Exception;

    /**
     * Register account
     *
     * @param registerInfoReqVO
     * @return
     * @throws Exception
     */
    BaseResponse<RegisterInfoResVO> registerAccount(@Valid RegisterInfoReqVO registerInfoReqVO) throws Exception;

    /**
     * Get all online users
     *
     * @return
     * @throws Exception
     */
    Object onlineUser() throws Exception;
}
