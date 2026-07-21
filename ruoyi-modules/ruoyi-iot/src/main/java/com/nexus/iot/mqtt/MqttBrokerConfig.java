package com.nexus.iot.mqtt;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.InterceptHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Moquette MQTT 内嵌 Broker 配置。
 * <p>
 * 启动内嵌 Broker 监听 {@code nexus.iot.mqtt.port}（默认 1883），
 * 注册设备认证器、消息拦截器与 Topic 访问控制策略；
 * {@code nexus.iot.mqtt.enabled=false} 时不启动。
 *
 * @author nexus
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(IotMqttProperties.class)
@ConditionalOnProperty(prefix = "nexus.iot.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttBrokerConfig {

    /** MQTT Broker 配置属性。 */
    private final IotMqttProperties properties;
    /** 设备认证器，校验 ClientID/密码。 */
    private final IotMqttAuthenticator authenticator;
    /** 消息拦截器，处理连接生命周期与属性上报。 */
    private final IotMqttInterceptHandler interceptHandler;
    /** Topic 访问控制策略，实现租户隔离。 */
    private final IotMqttAuthorizatorPolicy authorizatorPolicy;

    /**
     * 构建并启动内嵌 Moquette Broker：按属性配置主机/端口/匿名开关/WebSocket，
     * 注册拦截器、认证器与租户隔离授权策略；销毁时停止服务。
     *
     * @return 已启动的 Broker 服务实例
     * @throws IOException 启动失败
     */
    @Bean(destroyMethod = "stopServer")
    public Server mqttBrokerServer() throws IOException {
        IConfig config = new MemoryConfig(new Properties());
        config.setProperty(IConfig.HOST_PROPERTY_NAME, properties.getHost());
        config.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(properties.getPort()));
        // 禁用匿名连接，强制走设备认证
        config.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, String.valueOf(properties.isAllowAnonymous()));
        // 开发阶段关闭持久化（内存模式），避免本地遗留数据文件
        config.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        // WebSocket 端口（供浏览器 MQTT.js 接入），<=0 不启动
        if (properties.getWebsocketPort() > 0) {
            config.setProperty(IConfig.WEB_SOCKET_PORT_PROPERTY_NAME, String.valueOf(properties.getWebsocketPort()));
        }

        Server server = new Server();
        List<InterceptHandler> handlers = List.of(interceptHandler);
        // 使用租户隔离策略替代 PermitAllAuthorizatorPolicy，防止跨租户 Topic 访问
        server.startServer(config, handlers, null, authenticator, authorizatorPolicy);

        log.info("IoT MQTT Broker 已启动：host={}, port={}, allowAnonymous={}",
            properties.getHost(), properties.getPort(), properties.isAllowAnonymous());
        return server;
    }
}
