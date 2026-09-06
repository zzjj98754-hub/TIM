package com.tuling.tim.server.test;

import com.tuling.tim.client.TIMClientApplication;
import com.tuling.tim.client.service.RouteRequest;
import com.tuling.tim.client.vo.req.LoginReqVO;
import com.tuling.tim.client.vo.res.TIMServerResVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 * @since JDK 1.8
 */
@SpringBootTest(classes = TIMClientApplication.class)
@ExtendWith(SpringExtension.class)
public class RouteTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(RouteTest.class);

    @Value("${tim.user.id:0}")
    private long userId;

    @Value("${tim.user.userName:test}")
    private String userName;

    @Autowired
    private RouteRequest routeRequest ;

    @Test
    public void test() {
        LoginReqVO vo = new LoginReqVO(userId,userName) ;
        assertThrows(Exception.class, () -> routeRequest.getTIMServer(vo));
    }
}
