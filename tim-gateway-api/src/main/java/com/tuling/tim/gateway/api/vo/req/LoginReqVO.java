package com.tuling.tim.gateway.api.vo.req;

import com.tuling.tim.common.req.BaseRequest;

import jakarta.validation.constraints.NotNull;

/**
 * @since JDK 1.8
 */
public class LoginReqVO extends BaseRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;
    @NotNull(message = "userName 不能为空")
    private String userName;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        return "LoginReqVO{" +
                "userId=" + userId +
                ", userName='" + userName + '\'' +
                "} " + super.toString();
    }
}
