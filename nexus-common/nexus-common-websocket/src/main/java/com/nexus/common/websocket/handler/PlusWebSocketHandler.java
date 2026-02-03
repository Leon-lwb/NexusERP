package com.nexus.common.websocket.handler;

import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.websocket.dto.WebSocketMessageDto;
import com.nexus.common.websocket.holder.WebSocketSessionHolder;
import com.nexus.common.websocket.utils.WebSocketUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.List;

import static com.nexus.common.websocket.constant.WebSocketConstants.LOGIN_USER_KEY;

/**
 * WebSocketHandler 实现类
 *
 * @author zendwang
 */
@Slf4j
public class PlusWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * 连接成功后
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser loginUser = (LoginUser) session.getAttributes().get(LOGIN_USER_KEY);
        WebSocketSessionHolder.addSession(loginUser.getUsername(), session);
        log.info("[connect] sessionId: {},userName:{},userType:{}", session.getId(), loginUser.getUsername());
    }

    /**
     * 处理发送来的文本消息
     *
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        LoginUser loginUser = (LoginUser) session.getAttributes().get(LOGIN_USER_KEY);
        log.info("PlusWebSocketHandler, 连接：" + session.getId() + "，已收到消息:" + message.getPayload());
        List<String> userNames = List.of(loginUser.getUsername());
        WebSocketMessageDto webSocketMessageDto = new WebSocketMessageDto();
        webSocketMessageDto.setSessionKeys(userNames);
        webSocketMessageDto.setMessage(message.getPayload());
        WebSocketUtils.publishMessage(webSocketMessageDto);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        super.handleBinaryMessage(session, message);
    }

    /**
     * 心跳监测的回复
     *
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        WebSocketUtils.sendPongMessage(session);
    }

    /**
     * 连接出错时
     *
     * @param session
     * @param exception
     * @throws Exception
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[transport error] sessionId: {} , exception:{}", session.getId(), exception.getMessage());
    }

    /**
     * 连接关闭后
     *
     * @param session
     * @param status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LoginUser loginUser = (LoginUser) session.getAttributes().get(LOGIN_USER_KEY);
        WebSocketSessionHolder.removeSession(loginUser.getUsername());
        // log.info("[disconnect] sessionId: {},userName:{},userType:{}", session.getId(), loginUser.getUsername(), loginUser.getUserType());
        log.info("[disconnect] sessionId: {},userName:{},userType:{}", session.getId(), loginUser.getUsername());
    }

    /**
     * 是否支持分片消息
     *
     * @return
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

}
