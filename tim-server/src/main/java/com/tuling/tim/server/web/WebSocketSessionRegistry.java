package com.tuling.tim.server.web;
import org.springframework.web.socket.WebSocketSession;
import java.util.Map; import java.util.concurrent.ConcurrentHashMap;
public final class WebSocketSessionRegistry { private static final Map<Long,WebSocketSession> S=new ConcurrentHashMap<>(); private WebSocketSessionRegistry(){} public static WebSocketSession replace(long id,WebSocketSession s){return S.put(id,s);} public static WebSocketSession get(long id){return S.get(id);} public static void remove(long id,WebSocketSession s){S.remove(id,s);} }
