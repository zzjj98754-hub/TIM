package com.tuling.tim.server.web;
import org.springframework.context.annotation.Configuration; import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket public class ChatWebSocketConfig implements WebSocketConfigurer { private final ChatWebSocketHandler h; public ChatWebSocketConfig(ChatWebSocketHandler h){this.h=h;} public void registerWebSocketHandlers(WebSocketHandlerRegistry r){r.addHandler(h,"/ws/chat").setAllowedOriginPatterns("*");} }
