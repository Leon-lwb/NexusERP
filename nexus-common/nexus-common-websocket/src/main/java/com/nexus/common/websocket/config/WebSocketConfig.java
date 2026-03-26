package com.nexus.common.websocket.config;

import com.nexus.common.websocket.config.properties.WebSocketProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 配置
 *
 * @author nbacheng
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig {

    /**
     * 要扫描的 WebSocket 端点类全名列表
     * 注意：需要在这里显式列出所有 @ServerEndpoint 类的全限定名
     */
    private static final List<String> ENDPOINT_CLASS_NAMES = new ArrayList<>();

    static {
        // 添加 IM WebSocket 端点
        ENDPOINT_CLASS_NAMES.add("com.nexus.im.websocket.ImWebSocketServer");
    }

    /**
     * 配置 WebSocket 容器
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(8192);
        log.info("创建 WebSocket 容器配置");
        return container;
    }

	/**
	 * 开启WebSocket支持
	 */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        List<Class<?>> endpointClasses = new ArrayList<>();

        for (String className : ENDPOINT_CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                endpointClasses.add(clazz);
                log.info("加载 WebSocket 端点类: {}", className);
            } catch (ClassNotFoundException e) {
                log.warn("WebSocket 端点类未找到: {}，将在自动扫描时处理", className);
            }
        }

        log.info("创建 ServerEndpointExporter Bean，显式配置 {} 个端点", endpointClasses.size());
        ServerEndpointExporter exporter = new ServerEndpointExporter();
        if (!endpointClasses.isEmpty()) {
            exporter.setAnnotatedEndpointClasses(endpointClasses.toArray(new Class[0]));
        }
        return exporter;
    }

    /**
     * 确保 Servlet 容器初始化时输出调试信息
     */
    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return new ServletContextInitializer() {
            @Override
            public void onStartup(ServletContext servletContext) throws ServletException {
                log.info("ServletContext 初始化完成，WebSocket 端点应该已注册");
                log.info("ServletContext 路径: {}", servletContext.getContextPath());
            }
        };
    }
}
