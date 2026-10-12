package com.nexus.common.websocket;

import cn.dev33.satoken.exception.NotLoginException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.websocket.interceptor.PlusWebSocketInterceptor;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 认证失败不得把完整令牌写到日志，也不得返回成功握手状态。 */
class WebSocketHandshakeSecurityTest {
    @Test
    void rejectedTokenDoesNotLeakIntoLogs() {
        String secret = "test-sensitive-bearer-token";
        Logger logger = (Logger) LoggerFactory.getLogger(PlusWebSocketInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getLoginUser).thenThrow(NotLoginException.newInstance(
                "web", "-100", "token 已被冻结:" + secret, secret));
            ServerHttpResponse response = mock(ServerHttpResponse.class);

            boolean accepted = new PlusWebSocketInterceptor().beforeHandshake(mock(ServerHttpRequest.class), response,
                mock(WebSocketHandler.class), new HashMap<>());

            assertFalse(accepted);
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(secret)));
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}