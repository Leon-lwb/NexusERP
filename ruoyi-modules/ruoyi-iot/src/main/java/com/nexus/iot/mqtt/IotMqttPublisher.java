package com.nexus.iot.mqtt;

import io.moquette.broker.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IoT MQTT 下行发布器（IOT-PIPE-02 / §4#8 下行指令通道）。
 * <p>
 * 通过内嵌 Moquette Broker 的 {@code Server.internalPublish} 向设备 Topic 发布消息：
 * <ul>
 *   <li>无需设备与平台建立额外连接（平台即 Broker），QoS 取 AT_LEAST_ONCE；</li>
 *   <li>Broker 未启用（{@code nexus.iot.mqtt.enabled=false}）时 {@code ObjectProvider} 取不到实例，
 *       发布返回 false，由调用方落"未下发"留痕，**不伪造成功**；</li>
 *   <li>发布者 clientId 用平台保留标识 {@value #PLATFORM_CLIENT_ID}，避免回环投递给平台自身会话。</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@Component
public class IotMqttPublisher {

    /** 平台侧发布者标识（非设备，仅用于 Moquette 跳过回环投递） */
    public static final String PLATFORM_CLIENT_ID = "nexus-iot-server";

    private final ObjectProvider<Server> serverProvider;

    /** MQTT 报文 ID 生成器（QoS>0 需要非 0 报文 ID） */
    private final AtomicInteger messageId = new AtomicInteger(1);

    public IotMqttPublisher(ObjectProvider<Server> serverProvider) {
        this.serverProvider = serverProvider;
    }

    /**
     * 发布一条消息到指定 Topic。
     *
     * @param topic   Topic（如 {@code iot/{tenant}/{deviceCode}/property/set}）
     * @param payload 消息体（JSON 字符串）
     * @return 发布成功返回 true；Broker 未启用或发布异常返回 false
     */
    public boolean publish(String topic, String payload) {
        Server server = serverProvider.getIfAvailable();
        if (server == null) {
            log.warn("MQTT Broker 未启用，消息未发布：topic={}", topic);
            return false;
        }
        if (topic == null || topic.isBlank() || payload == null) {
            log.warn("MQTT 发布参数非法：topic={}", topic);
            return false;
        }
        try {
            ByteBuf buf = Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8);
            MqttPublishMessage message = MqttMessageBuilders.publish()
                .topicName(topic)
                .retained(false)
                .qos(MqttQoS.AT_LEAST_ONCE)
                .messageId(nextMessageId())
                .payload(buf)
                .build();
            server.internalPublish(message, PLATFORM_CLIENT_ID);
            if (log.isDebugEnabled()) {
                log.debug("MQTT 内部发布成功：topic={}, payload={}", topic, payload);
            }
            return true;
        } catch (Exception e) {
            log.error("MQTT 内部发布失败：topic={}, err={}", topic, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 生成下一个报文 ID（1~65535 循环）。
     */
    private int nextMessageId() {
        int id = messageId.getAndUpdate(v -> v >= 65535 ? 1 : v + 1);
        return id <= 0 ? 1 : id;
    }
}
