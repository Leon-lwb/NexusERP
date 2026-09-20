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
 * 认证方式：ClientID = deviceCode（唯一身份口径），username 若传则必须与 ClientID 相同，
 * password = deviceSecret。连接时按 ClientID 查询 iot_device，校验 device_secret 与状态。
 * <p>
 * 安全约束（IOT-MQTT-01/02）：不允许用他人 deviceCode 作为 ClientID 认证——
 * 否则任意持有有效密钥的设备都能冒充/顶掉任意设备（含跨租户注入数据）。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotMqttAuthenticator implements IAuthenticator {

    private final IIotDeviceService deviceService;

    /**
     * 校验 MQTT 连接身份：ClientID 即 deviceCode（唯一身份口径），password 即 deviceSecret。
     * 校验 username 与 ClientID 一致、设备存在、状态为 ACTIVE，且密钥以常量时间比较匹配。
     *
     * @param clientId MQTT ClientID（即设备编码，唯一身份来源）
     * @param username 用户名（可空；非空时必须等于 ClientID，否则视为身份伪造）
     * @param password 连接密码（即 deviceSecret 原文）
     * @return 校验通过返回 true，否则 false
     */
    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        // 身份口径统一为 ClientID：ClientID 必须等于被认证设备的 deviceCode
        if (StringUtils.isBlank(clientId) || password == null) {
            log.warn("MQTT 认证失败：缺少 ClientID 或密码，clientId={}", clientId);
            return false;
        }
        // username 若存在必须与 ClientID 一致，否则拒绝（防止拿自己密钥 + 他人 deviceCode 冒充/顶号）
        if (StringUtils.isNotBlank(username) && !clientId.equals(username)) {
            log.warn("MQTT 认证失败：username 与 ClientID 不一致（疑似身份伪造），clientId={}, username={}",
                clientId, username);
            return false;
        }
        String deviceCode = clientId;
        String secret = new String(password, StandardCharsets.UTF_8);
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
