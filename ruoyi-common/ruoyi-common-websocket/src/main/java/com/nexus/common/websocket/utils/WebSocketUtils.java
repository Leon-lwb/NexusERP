package com.nexus.common.websocket.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.websocket.dto.WebSocketMessageDto;
import com.nexus.common.websocket.holder.WebSocketSessionHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.function.Consumer;

import static com.nexus.common.websocket.constant.WebSocketConstants.WEB_SOCKET_TOPIC;

/**
 * 工具类
 *
 * @author zendwang
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebSocketUtils {

    /**
     * 向指定的WebSocket会话发送消息
     *
     * @param sessionKey 要发送消息的用户id
     * @param message    要发送的消息内容
     */
    public static void sendMessage(Long sessionKey, String message) {
        for (WebSocketSession session : WebSocketSessionHolder.getUserSessions(sessionKey)) {
            if (!sendMessage(session, new TextMessage(message))) {
                WebSocketSessionHolder.removeSession(sessionKey, session.getId());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignored) {
                    // 已失效连接不应阻断同账号其他设备的投递。
                }
            }
        }
    }

    /**
     * 订阅WebSocket消息主题，并提供一个消费者函数来处理接收到的消息
     *
     * @param consumer 处理WebSocket消息的消费者函数
     */
    public static void subscribeMessage(Consumer<WebSocketMessageDto> consumer) {
        RedisUtils.subscribe(WEB_SOCKET_TOPIC, WebSocketMessageDto.class, consumer);
    }

    /**
     * 发布WebSocket订阅消息
     *
     * @param webSocketMessage 要发布的WebSocket消息对象
     */
    public static void publishMessage(WebSocketMessageDto webSocketMessage) {
        // 空定向列表不得被订阅端解释为全员广播，广播只能显式调用 publishAll。
        if (webSocketMessage == null || webSocketMessage.getSessionKeys() == null
            || webSocketMessage.getSessionKeys().isEmpty()) {
            return;
        }
        // 同一用户可能在多个节点同时在线，统一广播后由各节点投递给本机连接。
        RedisUtils.publish(WEB_SOCKET_TOPIC, webSocketMessage, consumer -> {
            log.debug("WebSocket 已发布，接收用户数:{}", webSocketMessage.getSessionKeys().size());
        });
    }

    /**
     * 向所有的WebSocket会话发布订阅的消息(群发)
     *
     * @param message 要发布的消息内容
     */
    public static void publishAll(String message) {
        WebSocketMessageDto broadcastMessage = new WebSocketMessageDto();
        broadcastMessage.setMessage(message);
        RedisUtils.publish(WEB_SOCKET_TOPIC, broadcastMessage, consumer -> {
            log.debug("WebSocket 已发布广播消息，topic:{}", WEB_SOCKET_TOPIC);
        });
    }

    /**
     * 向指定的WebSocket会话发送Pong消息
     *
     * @param session 要发送Pong消息的WebSocket会话
     */
    public static void sendPongMessage(WebSocketSession session) {
        sendMessage(session, new PongMessage());
    }

    /**
     * 向指定的WebSocket会话发送文本消息
     *
     * @param session WebSocket会话
     * @param message 要发送的文本消息内容
     */
    public static void sendMessage(WebSocketSession session, String message) {
        sendMessage(session, new TextMessage(message));
    }

    /**
     * 向指定的WebSocket会话发送WebSocket消息对象
     *
     * @param session WebSocket会话
     * @param message 要发送的WebSocket消息对象
     */
    private static boolean sendMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(message);
            return true;
        } catch (Exception e) {
            // 装饰会话缓冲超限抛运行时异常，也必须在单个连接边界内隔离。
            log.warn("WebSocket 发送失败，sessionId:{}", session.getId(), e);
            return false;
        }
    }
}