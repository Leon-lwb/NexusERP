package com.nexus.iot.mqtt;

import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.pipeline.IotMessagePipelineService;
import org.springframework.stereotype.Component;

/**
 * IoT MQTT 消息拦截器。
 * <p>
 * 职责：
 * <ul>
 *   <li>onConnect — 更新设备 ONLINE（DB + Redis），若声明遗嘱则缓存 will payload；</li>
 *   <li>onDisconnect — 正常断开，置 OFFLINE 并清空 will；</li>
 *   <li>onConnectionLost — 异常断开（reason=CONNECTION_LOST），置 OFFLINE 并清空 will；</li>
 *   <li>onPublish — 解析属性上报并 XADD iot:data:stream（Week 34 数据管道生产端）。</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotMqttInterceptHandler extends AbstractInterceptHandler {

    /** 设备业务服务，用于按编码查询与更新连接状态。 */
    private final IIotDeviceService deviceService;
    /** 设备在线状态 Redis 缓存，维护 iot:status / iot:will。 */
    private final IotDeviceStatusCache statusCache;
    /** 消息管道服务，将属性上报写入 iot:data:stream。 */
    private final IotMessagePipelineService messagePipelineService;

    /**
     * 返回拦截器唯一标识。
     *
     * @return 拦截器 ID
     */
    @Override
    public String getID() {
        return "iot-mqtt-intercept-handler";
    }

    /**
     * Broker 会话循环发生异常时的回调，记录错误日志。
     *
     * @param error 异常
     */
    @Override
    public void onSessionLoopError(Throwable error) {
        log.error("MQTT Broker 会话循环异常", error);
    }

    /**
     * 设备连接成功：更新 ONLINE（DB + Redis），若声明遗嘱则缓存 will payload。
     *
     * @param msg 连接消息（含 ClientID 与遗嘱信息）
     */
    @Override
    public void onConnect(InterceptConnectMessage msg) {
        String deviceCode = msg.getClientID();
        log.info("MQTT 设备连接：deviceCode={}", deviceCode);
        IotDevice device = updateStatus(deviceCode, true);
        // 遗嘱消息：连接时声明，缓存以便排查异常断开
        if (device != null && msg.isWillFlag()) {
            byte[] will = msg.getWillMessage();
            String willPayload = will != null ? new String(will, CharsetUtil.UTF_8) : "";
            statusCache.storeWill(device.getDeviceId(), willPayload);
            log.debug("设备声明遗嘱：deviceCode={}, willTopic={}", deviceCode, msg.getWillTopic());
        }
    }

    /**
     * 设备正常断开：置 OFFLINE 并清空遗嘱缓存。
     *
     * @param msg 断开消息（含 ClientID）
     */
    @Override
    public void onDisconnect(InterceptDisconnectMessage msg) {
        String deviceCode = msg.getClientID();
        log.info("MQTT 设备正常断开：deviceCode={}", deviceCode);
        IotDevice device = updateStatus(deviceCode, false);
        if (device != null) {
            statusCache.clearWill(device.getDeviceId());
        }
    }

    /**
     * 设备异常连接丢失（reason=CONNECTION_LOST）：置 OFFLINE 并清空遗嘱缓存。
     *
     * @param msg 连接丢失消息（含 ClientID）
     */
    @Override
    public void onConnectionLost(InterceptConnectionLostMessage msg) {
        String deviceCode = msg.getClientID();
        log.warn("MQTT 设备连接丢失（reason=CONNECTION_LOST）：deviceCode={}", deviceCode);
        IotDevice device = updateStatus(deviceCode, false);
        if (device != null) {
            statusCache.clearWill(device.getDeviceId());
        }
    }

    /**
     * 设备属性上报：解析消息体并交由管道服务写入 iot:data:stream；解析/写入异常由管道内部吞掉，不影响 Broker。
     *
     * @param msg 发布消息（含 ClientID、Topic 与 payload）
     */
    @Override
    public void onPublish(InterceptPublishMessage msg) {
        // Week 34：解析消息并 XADD iot:data:stream；解析/写入异常由管道服务内部吞掉，不影响 Broker
        String deviceCode = msg.getClientID();
        String topic = msg.getTopicName();
        ByteBuf payload = msg.getPayload();
        String body = payload.toString(CharsetUtil.UTF_8);
        if (log.isDebugEnabled()) {
            log.debug("MQTT 属性上报：deviceCode={}, topic={}, payload={}", deviceCode, topic, body);
        }
        messagePipelineService.publishToStream(deviceCode, topic, body);
    }

    /**
     * 更新设备在线状态（DB connection_status + Redis iot:status）。
     *
     * @return 解析到的设备（用于后续 will 处理）；设备不存在返回 null
     */
    private IotDevice updateStatus(String deviceCode, boolean online) {
        IotDevice device = deviceService.selectEntityByCode(deviceCode);
        if (device == null) {
            log.warn("设备状态更新失败：设备不存在，deviceCode={}", deviceCode);
            return null;
        }
        deviceService.updateConnectionStatus(device.getDeviceId(), online);
        if (online) {
            statusCache.markOnline(device);
        } else {
            statusCache.markOffline(device);
        }
        return device;
    }
}
