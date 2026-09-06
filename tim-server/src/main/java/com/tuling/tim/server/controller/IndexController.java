package com.tuling.tim.server.controller;

import com.tuling.tim.common.enums.StatusEnum;
import com.tuling.tim.common.res.BaseResponse;
import com.tuling.tim.server.api.ServerApi;
import com.tuling.tim.server.api.vo.req.SendMsgReqVO;
import com.tuling.tim.server.api.vo.res.SendMsgResVO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @since JDK 1.8
 */
@Controller
@RequestMapping("/")
public class IndexController implements ServerApi {


    /**
     * @param sendMsgReqVO
     * @return
     */
    @Override
    @RequestMapping(value = "sendMsg", method = RequestMethod.POST)
    @ResponseBody
    public BaseResponse<SendMsgResVO> sendMsg(@RequestBody SendMsgReqVO sendMsgReqVO) {
        BaseResponse<SendMsgResVO> response = new BaseResponse<>();
        response.setCode(StatusEnum.FAIL.getCode());
        response.setMessage("Deprecated: send messages through the authenticated Netty connection");
        return response;
    }

}
