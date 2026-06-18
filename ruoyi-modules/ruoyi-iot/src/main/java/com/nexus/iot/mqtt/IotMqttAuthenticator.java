package com.nexus.iot.mqtt;

import io.moquette.broker.security.IAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * IoT MQTT 设备认证器。
 * <p>
 * 认证方式：ClientID = deviceCode，username = deviceCode，password = deviceSecret。
 * 连接时按 deviceCode 查询 iot_device，校验 device_secret 与状态。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotMqttAuthenticator implements IAuthenticator {

    private final IIotDeviceService deviceService;

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        // ClientID 即设备编码；username 兼容传入设备编码
        String deviceCode = StringUtils.isNotBlank(username) ? username : clientId;
        if (StringUtils.isBlank(deviceCode) || password == null) {
            log.warn("MQTT 认证失败：缺少设备编码或密码，clientId={}", clientId);
            return false;
        }
        String secret = new String(password);
        IotDevice device = deviceService.selectEntityByCode(deviceCode);
        if (device == null) {
            log.warn("MQTT 认证失败：设备不存在，deviceCode={}", deviceCode);
            return false;
        }
        if (!"ACTIVE".equals(device.getStatus())) {
            log.warn("MQTT 认证失败：设备未启用，deviceCode={}, status={}", deviceCode, device.getStatus());
            return false;
        }
        // 常量时间比较，防止时序攻击泄露密钥前缀匹配长度
        if (StringUtils.isBlank(device.getDeviceSecret())
            || !MessageDigest.isEqual(
                device.getDeviceSecret().getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("MQTT 认证失败：密钥不匹配，deviceCode={}", deviceCode);
            return false;
        }
        log.debug("MQTT 认证通过：deviceCode={}, deviceId={}", deviceCode, device.getDeviceId());
        return true;
    }
}
