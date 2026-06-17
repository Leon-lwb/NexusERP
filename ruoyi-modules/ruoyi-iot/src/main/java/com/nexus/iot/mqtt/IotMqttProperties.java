package com.nexus.iot.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IoT MQTT Broker 配置属性（前缀 nexus.iot.mqtt）
 * <p>
 * Moquette 内嵌 Broker：enabled=false 时不启动（便于测试或端口占用场景降级启动）。
 *
 * @author nexus
 */
@Data
@ConfigurationProperties(prefix = "nexus.iot.mqtt")
public class IotMqttProperties {

    /**
     * 是否启用内嵌 MQTT Broker
     */
    private boolean enabled = true;

    /**
     * 监听主机
     */
    private String host = "0.0.0.0";

    /**
     * 监听端口
     */
    private int port = 1883;

    /**
     * 是否允许匿名连接（生产环境必须为 false）
     */
    private boolean allowAnonymous = false;

    /**
     * 设备在线状态缓存 TTL（秒）
     */
    private long statusTtlSeconds = 3600;
}
