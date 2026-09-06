package com.tuling.tim.server.test;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.client.vo.res.OnlineUsersResVO;
import com.tuling.tim.client.vo.res.TIMServerResVO;
import com.vdurmont.emoji.EmojiParser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @since JDK 1.8
 */
public class CommonTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(CommonTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void test() throws Exception {

        String json = "{\"code\":\"9000\",\"message\":\"成功\",\"reqNo\":null,\"dataBody\":{\"ip\":\"127.0.0.1\",\"timServerPort\":8081,\"httpPort\":8082}}";

        TIMServerResVO timServerResVO = OBJECT_MAPPER.readValue(json, TIMServerResVO.class);

        System.out.println(timServerResVO.toString());

        String text = "nihaoaaa";
        String[] split = text.split(" ");
        System.out.println(split.length);
    }

    @Test
    public void onlineUser() throws Exception {
        List<OnlineUsersResVO.DataBodyBean> onlineUsers = new ArrayList<>(64);

        OnlineUsersResVO.DataBodyBean bodyBean = new OnlineUsersResVO.DataBodyBean();

        bodyBean.setUserId(100L);
        bodyBean.setUserName("zhangsan");
        onlineUsers.add(bodyBean);

        bodyBean = new OnlineUsersResVO.DataBodyBean();
        bodyBean.setUserId(200L);
        bodyBean.setUserName("tuling");
        onlineUsers.add(bodyBean);

        LOGGER.info("list={}", OBJECT_MAPPER.writeValueAsString(onlineUsers));

        LOGGER.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        for (OnlineUsersResVO.DataBodyBean onlineUser : onlineUsers) {

            LOGGER.info("userId={}=====userName={}", onlineUser.getUserId(), onlineUser.getUserName());
        }
        LOGGER.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }


    @Test
    public void searchMsg() {
        StringBuilder sb = new StringBuilder();
        String allMsg = "于是在之前的基础上我完善了一些内容，先来看看这个项目的介绍吧：\n" +
                "\n" +
                "TIM 一款面向开发者的 IM(即时通讯)系统；同时提供了一些组件帮助开发者构建一款属于自己可水平扩展的 IM 。\n" +
                "\n" +
                "借助 TIM 你可以实现以下需求：";

        String key = "IM";

        String[] split = allMsg.split("\n");
        for (String msg : split) {
            if (msg.trim().contains(key)) {
                sb.append(msg).append("\n");
            }
        }
        int pos = 0;

        String result = sb.toString();

        int count = 1;
        int multiple = 2;
        while ((pos = result.indexOf(key, pos)) >= 0) {

            LOGGER.info("{},{}", pos, pos + key.length());

            if (count == 1) {
                sb.insert(pos, "**");
            } else {
                Double pow = Math.pow(multiple, count);
                sb.insert(pos + pow.intValue(), "**");
            }

            pos += key.length();

            if (count == 1) {
                sb.insert(pos + 2, "**");
            } else {
                Double pow = Math.pow(multiple, count);
                sb.insert((pos + 2) + pow.intValue(), "**");

            }


            count++;
        }

        System.out.println(sb);
    }

    @Test
    public void searchMsg2() {
        StringBuilder sb = new StringBuilder();
        String allMsg = "于是在之前的基础上我完善了一些内容，先来看看这个项目的介绍吧：\n" +
                "\n" +
                "TIM 一款面向开发者的 IM(即时通讯)系统；同时提供了一些组件帮助开发者构建一款属于自己可水平扩展的 IM 。\n" +
                "\n" +
                "借助 TIM 你可以实现以下需求：";

        String key = "TIM";

        String[] split = allMsg.split("\n");
        for (String msg : split) {
            if (msg.trim().contains(key)) {
                sb.append(msg).append("\n");
            }
        }
        int pos = 0;

        String result = sb.toString();

        int count = 1;
        int multiple = 2;
        while ((pos = result.indexOf(key, pos)) >= 0) {

            LOGGER.info("{},{}", pos, pos + key.length());

            pos += key.length();


            count++;
        }

        System.out.println(sb.toString());
        System.out.println(sb.toString().replace(key, "\033[31;4m" + key + "\033[0m"));
    }

    @Test
    public void log() {
        String msg = "hahahdsadsd";
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();

        String dir = "/opt/logs/tim/zhangsan" + "/";
        String fileName = dir + year + month + day + ".log";
        LOGGER.info("fileName={}", fileName);

        Path file = Paths.get(fileName);
        boolean exists = Files.exists(Paths.get(dir), LinkOption.NOFOLLOW_LINKS);
        try {
            if (!exists) {
                Files.createDirectories(Paths.get(dir));
            }

            List<String> lines = Arrays.asList(msg);

            Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.info("IOException", e);
        }

    }

    @Test
    public void emoji() throws Exception {
        String str = "An :grinning:awesome :smiley:string &#128516;with a few :wink:emojis!";
        String result = EmojiParser.parseToUnicode(str);
        System.out.println(result);


        result = EmojiParser.parseToAliases(str);
        System.out.println(result);
//
//        Collection<Emoji> all = EmojiManager.getAll();
//        for (Emoji emoji : all) {
//            System.out.println(EmojiParser.parseToAliases(emoji.getUnicode())  + "--->" + emoji.getUnicode() );
//        }

    }

    @Test
    public void emoji2() {
        String emostring = "😂";

        String face_with_tears_of_joy = emostring.replaceAll("\uD83D\uDE02", "face with tears of joy");
        System.out.println(face_with_tears_of_joy);

        System.out.println("======" + face_with_tears_of_joy.replaceAll("face with tears of joy", "\uD83D\uDE02"));
    }

}
