package com.nexus.im.config;

import com.nexus.im.service.ImUserStatusService;
import com.nexus.im.websocket.ImWebSocketServer;
import com.nexus.im.websocket.ImWebSocketStatusManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Import;

/**
 * IM WebSocket 配置
 * 初始化 WebSocket 服务依赖
 *
 * @author nexus
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(name = "com.nexus.common.websocket.config.WebSocketConfig")
@Import(ImWebSocketServer.class)
@RequiredArgsConstructor
public class ImWebSocketConfig {

    private final ImUserStatusService imUserStatusService;
    private final ImWebSocketStatusManager imWebSocketStatusManager;

    @PostConstruct
    public void init() {
        // 将服务注入到 WebSocketServer 中
        ImWebSocketServer.setUserStatusService(imUserStatusService);
        ImWebSocketServer.setStatusManager(imWebSocketStatusManager);
        log.info("IM WebSocket 配置初始化完成");

        // 验证端点类是否正确加载
        try {
            Class<?> clazz = Class.forName("com.nexus.im.websocket.ImWebSocketServer");
            jakarta.websocket.server.ServerEndpoint annotation = clazz.getAnnotation(jakarta.websocket.server.ServerEndpoint.class);
            if (annotation != null) {
                log.info("【IM WebSocket】端点类验证成功，路径: {}", annotation.value());
            } else {
                log.warn("【IM WebSocket】端点类没有 @ServerEndpoint 注解");
            }
        } catch (ClassNotFoundException e) {
            log.error("【IM WebSocket】端点类未找到", e);
        }
    }
}
