package com.nexus.iot.mqtt;

import io.moquette.broker.security.IAuthorizatorPolicy;
import io.moquette.broker.subscriptions.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import org.springframework.stereotype.Component;

/**
 * IoT MQTT Topic 访问控制策略。
 * <p>
 * 替代 {@code PermitAllAuthorizatorPolicy}，基于设备租户隔离 Topic 访问：
 * <ul>
 *   <li>Topic 规约：{@code iot/{tenantId}/{deviceCode}/{bizType}/{action}}</li>
 *   <li>Publish：设备只能向自己租户的 Topic 发布消息；</li>
 *   <li>Subscribe：设备只能订阅自己租户的 Topic。</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotMqttAuthorizatorPolicy implements IAuthorizatorPolicy {

    private static final String TOPIC_PREFIX = "iot";
    private final IIotDeviceService deviceService;

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        String topicStr = topic.toString();
        // 以 clientId（即 deviceCode）查询设备租户
        String deviceCode = StringUtils.isNotBlank(user) ? user : client;
        String tenantId = resolveDeviceTenant(deviceCode);
        if (tenantId == null) {
            log.warn("MQTT 写权限拒绝：设备不存在或未启用，deviceCode={}, topic={}", deviceCode, topicStr);
            return false;
        }
        // 解析 Topic 中的 tenantId
        String topicTenantId = extractTenantId(topicStr);
        if (topicTenantId == null) {
            log.warn("MQTT 写权限拒绝：Topic 格式非法，topic={}", topicStr);
            return false;
        }
        if (!tenantId.equals(topicTenantId)) {
            log.warn("MQTT 写权限拒绝：跨租户访问，deviceCode={}, deviceTenant={}, topicTenant={}, topic={}",
                deviceCode, tenantId, topicTenantId, topicStr);
            return false;
        }
        return true;
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        String topicStr = topic.toString();
        String deviceCode = StringUtils.isNotBlank(user) ? user : client;
        String tenantId = resolveDeviceTenant(deviceCode);
        if (tenantId == null) {
            log.warn("MQTT 订阅权限拒绝：设备不存在或未启用，deviceCode={}, topic={}", deviceCode, topicStr);
            return false;
        }
        if (!topicStr.startsWith(TOPIC_PREFIX + "/")) {
            log.debug("MQTT 订阅权限拒绝：非 IoT Topic，topic={}", topicStr);
            return false;
        }
        String topicTenantId = extractTenantId(topicStr);
        if (topicTenantId == null) {
            log.warn("MQTT 订阅权限拒绝：Topic 格式非法，topic={}", topicStr);
            return false;
        }
        // 不允许通配符租户（+/#）
        if ("+".equals(topicTenantId) || "#".equals(topicTenantId)) {
            log.warn("MQTT 订阅权限拒绝：不允许通配符租户，deviceCode={}, topic={}", deviceCode, topicStr);
            return false;
        }
        if (!tenantId.equals(topicTenantId)) {
            log.warn("MQTT 订阅权限拒绝：跨租户访问，deviceCode={}, deviceTenant={}, topicTenant={}, topic={}",
                deviceCode, tenantId, topicTenantId, topicStr);
            return false;
        }
        return true;
    }

    /**
     * 从 Topic 中提取租户ID。
     * Topic 格式：iot/{tenantId}/{deviceCode}/{bizType}/{action}
     *
     * @return tenantId，格式非法返回 null
     */
    private String extractTenantId(String topic) {
        if (StringUtils.isBlank(topic)) {
            return null;
        }
        String[] seg = topic.split("/");
        if (seg.length < 2 || !TOPIC_PREFIX.equals(seg[0])) {
            return null;
        }
        return seg[1];
    }

    /**
     * 查询设备的租户ID。
     *
     * @return tenantId，设备不存在或未启用返回 null
     */
    private String resolveDeviceTenant(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            return null;
        }
        IotDevice device = deviceService.selectEntityByCode(deviceCode);
        if (device == null || !"ACTIVE".equals(device.getStatus())) {
            return null;
        }
        return device.getTenantId();
    }
}
