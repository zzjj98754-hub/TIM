package com.tuling.tim.client.service.impl;

import com.tuling.tim.client.TIMClientApplication;
import com.tuling.tim.client.service.MsgLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = TIMClientApplication.class)
@ExtendWith(SpringExtension.class)
public class AsyncMsgLoggerTest {


    @Autowired
    private MsgLogger msgLogger;

    @Test
    public void writeLog() throws Exception {
        for (int i = 0; i < 10; i++) {
            msgLogger.log("zhangsan:【asdsd】" + i);
        }

        TimeUnit.SECONDS.sleep(2);
    }


    @Test
    public void query() {
        String tuling = msgLogger.query("tuling");
        System.out.println(tuling);
    }

}