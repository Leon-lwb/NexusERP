package com.nexus.common.websocket.holder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 保存用户的所有在线连接，支持手机、桌面和多标签页同时接收消息。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebSocketSessionHolder {

    private static final Map<Long, Map<String, WebSocketSession>> USER_SESSION_MAP = new ConcurrentHashMap<>();

    /** 注册单个连接；用户其他设备的连接继续保留。 */
    public static void addSession(Long sessionKey, WebSocketSession session) {
        USER_SESSION_MAP.compute(sessionKey, (key, sessions) -> {
            Map<String, WebSocketSession> current = sessions == null ? new ConcurrentHashMap<>() : sessions;
            current.put(session.getId(), session);
            return current;
        });
    }

    /** 主动下线用户时关闭其全部连接。 */
    public static void removeSession(Long sessionKey) {
        Map<String, WebSocketSession> sessions = USER_SESSION_MAP.remove(sessionKey);
        if (sessions != null) {
            sessions.values().forEach(WebSocketSessionHolder::closeSession);
        }
    }

    /** 关闭回调仅移除自己的连接，旧连接关闭不得影响新连接。 */
    public static void removeSession(Long sessionKey, String sessionId) {
        USER_SESSION_MAP.computeIfPresent(sessionKey, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /** 保留单连接查询接口，消息投递使用完整连接列表。 */
    public static WebSocketSession getSessions(Long sessionKey) {
        return getUserSessions(sessionKey).stream().findFirst().orElse(null);
    }

    /** 返回连接快照，避免发送期间的增删改变遍历。 */
    public static Collection<WebSocketSession> getUserSessions(Long sessionKey) {
        Map<String, WebSocketSession> sessions = USER_SESSION_MAP.get(sessionKey);
        return sessions == null ? List.of() : List.copyOf(sessions.values());
    }

    public static Set<Long> getSessionsAll() {
        return Set.copyOf(USER_SESSION_MAP.keySet());
    }

    public static Boolean existSession(Long sessionKey) {
        Map<String, WebSocketSession> sessions = USER_SESSION_MAP.get(sessionKey);
        return sessions != null && !sessions.isEmpty();
    }

    private static void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // 连接可能已经关闭，仍需完成本地清理。
        }
    }
}