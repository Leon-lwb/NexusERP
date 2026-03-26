package com.nexus.im.websocket;

import com.alibaba.fastjson2.JSONObject;
import com.nexus.im.service.ImUserStatusService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * IM WebSocket 服务端点
 * 处理 IM 实时消息和在线状态管理
 *
 * @author nexus
 */
@Slf4j
@Component
@ServerEndpoint("/im/websocket/{userId}")
public class ImWebSocketServer {

    private Session session;
    private Long userId;

    // 所有连接集合
    private static final CopyOnWriteArraySet<ImWebSocketServer> webSockets = new CopyOnWriteArraySet<>();
    // 用户会话池: userId -> Set<Session>
    private static final Map<Long, Set<Session>> sessionPool = new ConcurrentHashMap<>();

    // 静态引用，用于非 Spring 管理的类中访问
    private static ImUserStatusService staticUserStatusService;
    private static ImWebSocketStatusManager staticStatusManager;

    static {
        log.info("【IM WebSocket】ImWebSocketServer 类被加载");
    }

    public ImWebSocketServer() {
        log.info("【IM WebSocket】ImWebSocketServer 实例被创建");
    }

    // Spring 注入的 setter
    public static void setUserStatusService(ImUserStatusService service) {
        staticUserStatusService = service;
    }

    public static void setStatusManager(ImWebSocketStatusManager manager) {
        staticStatusManager = manager;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "userId") String userIdStr) {
        try {
            this.session = session;
            this.userId = Long.valueOf(userIdStr);
            
            webSockets.add(this);
            sessionPool.computeIfAbsent(this.userId, key -> ConcurrentHashMap.newKeySet()).add(session);
            
            // 更新用户在线状态
            log.info("【IM WebSocket】用户连接: userId={}, staticStatusManager={}, staticUserStatusService={}", 
                this.userId, staticStatusManager != null, staticUserStatusService != null);
            
            if (staticStatusManager != null) {
                staticStatusManager.userConnected(this.userId);
                log.info("【IM WebSocket】通过 StatusManager 设置用户在线: userId={}", this.userId);
            } else if (staticUserStatusService != null) {
                staticUserStatusService.setUserOnline(this.userId);
                log.info("【IM WebSocket】通过 UserStatusService 设置用户在线: userId={}", this.userId);
            } else {
                log.warn("【IM WebSocket】无法设置用户在线状态，服务未注入: userId={}", this.userId);
            }
            
            log.info("【IM WebSocket】用户连接成功: userId={}, 总连接数={}", this.userId, webSockets.size());
            
            // 发送连接成功消息
            sendMessageToUser(this.userId, "{\"type\":\"connected\",\"message\":\"连接成功\"}");
        } catch (Exception e) {
            log.error("【IM WebSocket】连接异常: userId={}", userIdStr, e);
        }
    }

    @OnClose
    public void onClose() {
        try {
            webSockets.remove(this);
            
            if (this.userId != null) {
                Set<Session> sessions = sessionPool.get(this.userId);
                if (sessions != null) {
                    sessions.remove(this.session);
                    if (sessions.isEmpty()) {
                        sessionPool.remove(this.userId);
                    }
                }
                
                // 更新用户离线状态
                if (staticStatusManager != null) {
                    staticStatusManager.userDisconnected(this.userId);
                } else if (staticUserStatusService != null) {
                    staticUserStatusService.setUserOffline(this.userId);
                }
                
                log.info("【IM WebSocket】用户断开: userId={}, 总连接数={}", this.userId, webSockets.size());
            }
        } catch (Exception e) {
            log.error("【IM WebSocket】断开异常", e);
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.debug("【IM WebSocket】收到消息: userId={}, message={}", this.userId, message);
        
        try {
            // 更新用户最后活跃时间
            if (staticStatusManager != null && this.userId != null) {
                staticStatusManager.updateUserActiveTime(this.userId);
            } else if (staticUserStatusService != null && this.userId != null) {
                staticUserStatusService.updateLastActiveTime(this.userId);
            }
            
            // 处理心跳
            if ("ping".equalsIgnoreCase(message) || "{\"type\":\"ping\"}".equals(message)) {
                session.getBasicRemote().sendText("{\"type\":\"pong\"}");
                return;
            }
            
            // 解析消息
            JSONObject jsonMessage = JSONObject.parseObject(message);
            String type = jsonMessage.getString("type");
            
            // 处理不同类型的消息
            switch (type) {
                case "heartbeat":
                    session.getBasicRemote().sendText("{\"type\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}");
                    break;
                case "message":
                    // 处理聊天消息
                    handleChatMessage(jsonMessage);
                    break;
                default:
                    log.debug("【IM WebSocket】未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("【IM WebSocket】消息处理异常", e);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("【IM WebSocket】发生错误: userId={}, error={}", this.userId, error.getMessage());
    }

    /**
     * 发送消息给指定用户
     *
     * @param userId  用户ID
     * @param message 消息内容
     */
    public static void sendMessageToUser(Long userId, String message) {
        Set<Session> sessions = sessionPool.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        for (Session session : sessions) {
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    log.error("【IM WebSocket】发送消息失败: userId={}", userId, e);
                }
            }
        }
    }

    /**
     * 广播消息给所有在线用户
     *
     * @param message 消息内容
     */
    public static void broadcastMessage(String message) {
        for (ImWebSocketServer webSocket : webSockets) {
            if (webSocket.session != null && webSocket.session.isOpen()) {
                try {
                    webSocket.session.getAsyncRemote().sendText(message);
                } catch (Exception e) {
                    log.error("【IM WebSocket】广播消息失败", e);
                }
            }
        }
    }

    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return true-在线
     */
    public static boolean isUserOnline(Long userId) {
        Set<Session> sessions = sessionPool.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 获取在线用户数量
     *
     * @return 在线用户数
     */
    public static int getOnlineUserCount() {
        return sessionPool.size();
    }

    /**
     * 获取用户会话数量
     *
     * @param userId 用户ID
     * @return 会话数量
     */
    public static int getUserSessionCount(Long userId) {
        Set<Session> sessions = sessionPool.get(userId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * 处理聊天消息
     *
     * @param message 消息对象
     */
    private void handleChatMessage(JSONObject message) {
        // 这里可以集成现有的消息处理逻辑
        Long toUserId = message.getLong("toUserId");
        String content = message.getString("content");
        
        if (toUserId != null && content != null) {
            // 转发消息给目标用户
            JSONObject forwardMsg = new JSONObject();
            forwardMsg.put("type", "message");
            forwardMsg.put("fromUserId", this.userId);
            forwardMsg.put("content", content);
            forwardMsg.put("timestamp", System.currentTimeMillis());
            
            sendMessageToUser(toUserId, forwardMsg.toJSONString());
        }
    }
}
