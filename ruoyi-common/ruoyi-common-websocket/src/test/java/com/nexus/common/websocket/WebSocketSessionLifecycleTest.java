package com.nexus.common.websocket;

import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.websocket.handler.PlusWebSocketHandler;
import com.nexus.common.websocket.holder.WebSocketSessionHolder;
import com.nexus.common.websocket.utils.WebSocketUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 同一用户多端连接与旧连接关闭竞态回归。 */
class WebSocketSessionLifecycleTest {
    private static final Long USER_ID = 2109242152783400961L;

    @AfterEach
    void clearSessions() {
        WebSocketSessionHolder.removeSession(USER_ID);
    }

    @Test
    void multipleDevicesReceiveTheSameMessage() throws Exception {
        WebSocketSession first = session("first");
        WebSocketSession second = session("second");
        WebSocketSessionHolder.addSession(USER_ID, first);
        WebSocketSessionHolder.addSession(USER_ID, second);

        WebSocketUtils.sendMessage(USER_ID, "通知");

        verify(first).sendMessage(new TextMessage("通知"));
        verify(second).sendMessage(new TextMessage("通知"));
        verify(first, never()).close(any(CloseStatus.class));
    }

    @Test
    void closingOldConnectionDoesNotRemoveNewConnection() throws Exception {
        WebSocketSession oldSession = session("old");
        WebSocketSession newSession = session("new");
        WebSocketSessionHolder.addSession(USER_ID, oldSession);
        WebSocketSessionHolder.addSession(USER_ID, newSession);

        new PlusWebSocketHandler().afterConnectionClosed(oldSession, CloseStatus.NORMAL);
        WebSocketUtils.sendMessage(USER_ID, "新连接仍收到消息");

        verify(newSession).sendMessage(new TextMessage("新连接仍收到消息"));
        verify(newSession, never()).close(any(CloseStatus.class));
        assertTrue(WebSocketSessionHolder.existSession(USER_ID));
    }

    @Test
    void duplicateRegistrationDoesNotCloseCurrentConnection() throws Exception {
        WebSocketSession current = session("current");
        WebSocketSessionHolder.addSession(USER_ID, current);
        WebSocketSessionHolder.addSession(USER_ID, current);
        WebSocketUtils.sendMessage(USER_ID, "只发送一次");

        verify(current, times(1)).sendMessage(new TextMessage("只发送一次"));
        verify(current, never()).close(any(CloseStatus.class));
    }

    @Test
    void failedConnectionDoesNotInterruptOtherDevices() throws Exception {
        WebSocketSession failed = session("failed");
        WebSocketSession healthy = session("healthy");
        doThrow(new IllegalStateException("发送缓冲已满")).when(failed).sendMessage(any());
        WebSocketSessionHolder.addSession(USER_ID, failed);
        WebSocketSessionHolder.addSession(USER_ID, healthy);

        assertDoesNotThrow(() -> WebSocketUtils.sendMessage(USER_ID, "设备隔离"));

        verify(healthy).sendMessage(new TextMessage("设备隔离"));
        assertFalse(WebSocketSessionHolder.getUserSessions(USER_ID).contains(failed));
        verify(failed).close(any(CloseStatus.class));
    }
    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        LoginUser user = new LoginUser();
        user.setUserId(USER_ID);
        when(session.getAttributes()).thenReturn(Map.of("loginUser", user));
        return session;
    }
}