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
import org.springframework.stereotype.Component;

/**
 * IoT MQTT 消息拦截器。
 * <p>
 * Week 33 职责：
 * <ul>
 *   <li>onConnect / onDisconnect / onConnectionLost — 更新设备在线状态（DB + Redis）</li>
 *   <li>onPublish — 仅记录日志（Week 34 由 MqttMessageListener 写入 Redis Stream）</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotMqttInterceptHandler extends AbstractInterceptHandler {

    private final IIotDeviceService deviceService;
    private final IotDeviceStatusCache statusCache;

    @Override
    public String getID() {
        return "iot-mqtt-intercept-handler";
    }

    @Override
    public void onSessionLoopError(Throwable error) {
        log.error("MQTT Broker 会话循环异常", error);
    }

    @Override
    public void onConnect(InterceptConnectMessage msg) {
        String deviceCode = msg.getClientID();
        log.info("MQTT 设备连接：deviceCode={}", deviceCode);
        updateStatus(deviceCode, true);
    }

    @Override
    public void onDisconnect(InterceptDisconnectMessage msg) {
        String deviceCode = msg.getClientID();
        log.info("MQTT 设备断开：deviceCode={}", deviceCode);
        updateStatus(deviceCode, false);
    }

    @Override
    public void onConnectionLost(InterceptConnectionLostMessage msg) {
        String deviceCode = msg.getClientID();
        log.warn("MQTT 设备连接丢失：deviceCode={}", deviceCode);
        updateStatus(deviceCode, false);
    }

    @Override
    public void onPublish(InterceptPublishMessage msg) {
        // Week 33：仅记录日志；Week 34 改为解析消息并 XADD iot:data:stream
        String deviceCode = msg.getClientID();
        String topic = msg.getTopicName();
        ByteBuf payload = msg.getPayload();
        String body = payload.toString(CharsetUtil.UTF_8);
        log.debug("MQTT 属性上报：deviceCode={}, topic={}, payload={}", deviceCode, topic, body);
    }

    private void updateStatus(String deviceCode, boolean online) {
        IotDevice device = deviceService.selectEntityByCode(deviceCode);
        if (device == null) {
            log.warn("设备状态更新失败：设备不存在，deviceCode={}", deviceCode);
            return;
        }
        deviceService.updateConnectionStatus(device.getDeviceId(), online);
        if (online) {
            statusCache.markOnline(device);
        } else {
            statusCache.markOffline(device);
        }
    }
}
