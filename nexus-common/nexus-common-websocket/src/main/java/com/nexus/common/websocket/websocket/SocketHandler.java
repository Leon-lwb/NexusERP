package com.nexus.common.websocket.websocket;

import cn.hutool.core.util.ObjectUtil;
import com.nexus.common.base.BaseMap;
import com.nexus.common.constant.CommonSendStatus;
import com.nexus.common.websocket.listener.NbcioRedisListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 监听消息(采用redis发布订阅方式发送消息)
 */
@Slf4j
@Component
public class SocketHandler implements NbcioRedisListener {

    @Autowired
    private WebSocketServer webSocket;

    @Override
    public void onMessage(BaseMap map) {
        log.info("【SocketHandler消息】Redis Listerer:" + map.toString());

        String userName= map.get("userName");
        String message = map.get("message");
        if (ObjectUtil.isNotEmpty(userName)) {
            webSocket.pushMessage(userName, message);
            //app端消息推送
            webSocket.pushMessage(userName+CommonSendStatus.APP_SESSION_SUFFIX, message);
        } else {
            webSocket.pushMessage(message);
        }

    }
}