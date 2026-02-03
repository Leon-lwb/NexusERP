package com.nexus.common.websocket.utils;

import cn.hutool.core.collection.CollUtil;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.redis.RedisCache;
import com.nexus.common.websocket.dto.WebSocketMessageDto;
import com.nexus.common.websocket.holder.WebSocketSessionHolder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.nexus.common.websocket.constant.WebSocketConstants.LOGIN_USER_KEY;
import static com.nexus.common.websocket.constant.WebSocketConstants.WEB_SOCKET_TOPIC;

/**
 * 工具类
 *
 * @author nbacheng
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebSocketUtils {

    /**
     * 发送消息
     *
     * @param sessionKey session主键 一般为用户名
     * @param message    消息文本
     */
    public static void sendMessage(String sessionKey, String message) {
        WebSocketSession session = WebSocketSessionHolder.getSessions(sessionKey);
        sendMessage(session, message);
    }

    /**
     * 订阅消息
     *
     * @param consumer 自定义处理
     */
    public static void subscribeMessage(Consumer<WebSocketMessageDto> consumer) {
        RedisCache.subscribe(WEB_SOCKET_TOPIC, WebSocketMessageDto.class, consumer);
    }

    /**
     * 发布订阅的消息
     *
     * @param webSocketMessage 消息对象
     */
    public static void publishMessage(WebSocketMessageDto webSocketMessage) {
        List<String> unsentSessionKeys = new ArrayList<>();
        // 当前服务内session,直接发送消息
        for (String sessionKey : webSocketMessage.getSessionKeys()) {
            if (WebSocketSessionHolder.existSession(sessionKey)) {
                WebSocketUtils.sendMessage(sessionKey, webSocketMessage.getMessage());
                continue;
            }
            unsentSessionKeys.add(sessionKey);
        }
        // 不在当前服务内session,发布订阅消息
        if (CollUtil.isNotEmpty(unsentSessionKeys)) {
            WebSocketMessageDto broadcastMessage = new WebSocketMessageDto();
            broadcastMessage.setMessage(webSocketMessage.getMessage());
            broadcastMessage.setSessionKeys(unsentSessionKeys);
            RedisCache.publish(WEB_SOCKET_TOPIC, broadcastMessage, consumer -> {
                log.info(" WebSocket发送主题订阅消息topic:{} session keys:{} message:{}",
                    WEB_SOCKET_TOPIC, unsentSessionKeys, webSocketMessage.getMessage());
            });
        }
    }

    /**
     * 发布订阅的消息(群发)
     *
     * @param message 消息内容
     */
    public static void publishAll(String message) {
        WebSocketMessageDto broadcastMessage = new WebSocketMessageDto();
        broadcastMessage.setMessage(message);
        RedisCache.publish(WEB_SOCKET_TOPIC, broadcastMessage, consumer -> {
            log.info("WebSocket发送主题订阅消息topic:{} message:{}", WEB_SOCKET_TOPIC, message);
        });
    }

    public static void sendPongMessage(WebSocketSession session) {
        sendMessage(session, new PongMessage());
    }

    public static void sendMessage(WebSocketSession session, String message) {
        sendMessage(session, new TextMessage(message));
    }

    private static void sendMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (session == null || !session.isOpen()) {
            log.warn("[send] session会话已经关闭");
        } else {
            try {
                // 获取当前会话中的用户
                LoginUser loginUser = (LoginUser) session.getAttributes().get(LOGIN_USER_KEY);
                session.sendMessage(message);
                log.info("[send] sessionId: {},userName:{},userType:{},message:{}", session.getId(), loginUser.getUsername(),  message);
            } catch (IOException e) {
                log.error("[send] session({}) 发送消息({}) 异常", session, message, e);
            }
        }
    }
}
