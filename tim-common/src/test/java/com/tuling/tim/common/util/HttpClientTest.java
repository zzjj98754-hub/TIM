package com.tuling.tim.common.util;

import com.alibaba.fastjson.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class HttpClientTest {

    private OkHttpClient okHttpClient;
    private HttpServer server;
    private int port;

    @Before
    public void before() throws IOException {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        okHttpClient = builder.build();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/sendMsg", new JsonHandler(200, "{\"code\":\"9000\",\"message\":\"ok\"}"));
        server.start();
    }

    @After
    public void after() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void call() throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("msg", "hello");
        jsonObject.put("userId", 1586617710861L);

        Assert.assertTrue(HttpClient.call(okHttpClient, jsonObject.toString(), "http://127.0.0.1:" + port + "/sendMsg").isSuccessful());
    }

    @Test(expected = IOException.class)
    public void callFailWhenHttpStatusIsError() throws IOException {
        server.removeContext("/sendMsg");
        server.createContext("/sendMsg", new JsonHandler(500, "{\"code\":\"4000\"}"));

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("msg", "hello");
        jsonObject.put("userId", 1586617710861L);

        HttpClient.call(okHttpClient, jsonObject.toString(), "http://127.0.0.1:" + port + "/sendMsg");
    }

    private static final class JsonHandler implements HttpHandler {
        private final int status;
        private final byte[] body;

        private JsonHandler(int status, String body) {
            this.status = status;
            this.body = body.getBytes();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
