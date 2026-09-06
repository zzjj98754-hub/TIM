package com.tuling.tim.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class HttpClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OkHttpClient okHttpClient;
    private HttpServer server;
    private int port;

    @BeforeEach
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

    @AfterEach
    public void after() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void call() throws IOException {
        String json = OBJECT_MAPPER.writeValueAsString(new Payload("hello", 1586617710861L));

        assertTrue(HttpClient.call(okHttpClient, json, "http://127.0.0.1:" + port + "/sendMsg").isSuccessful());
    }

    @Test
    public void callFailWhenHttpStatusIsError() throws IOException {
        server.removeContext("/sendMsg");
        server.createContext("/sendMsg", new JsonHandler(500, "{\"code\":\"4000\"}"));

        String json = OBJECT_MAPPER.writeValueAsString(new Payload("hello", 1586617710861L));

        assertThrows(IOException.class,
                () -> HttpClient.call(okHttpClient, json, "http://127.0.0.1:" + port + "/sendMsg"));
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

    private static final class Payload {
        private final String msg;
        private final long userId;

        private Payload(String msg, long userId) {
            this.msg = msg;
            this.userId = userId;
        }

        public String getMsg() {
            return msg;
        }

        public long getUserId() {
            return userId;
        }
    }
}
