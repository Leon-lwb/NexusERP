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
 * 替代 {@code PermitAllAuthorizatorPolicy}，基于「设备租户 + 设备编码」双重隔离 Topic 访问：
 * <ul>
 *   <li>Topic 规约：{@code iot/{tenantId}/{deviceCode}/{bizType}/{action}}</li>
 *   <li>身份口径：一律以 MQTT {@code clientId}（即 deviceCode）为准，{@code username} 不参与身份判定；</li>
 *   <li>Publish：设备只能向 {@code 自己租户 + 自己 deviceCode} 的 Topic 发布消息；</li>
 *   <li>Subscribe：设备只能订阅 {@code 自己租户 + 自己 deviceCode} 的 Topic，禁止通配符租户与通配符设备段。</li>
 * </ul>
 * 当前系统未使用任何共享/广播 Topic，故未设白名单；如后续新增，需在此显式放行。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotMqttAuthorizatorPolicy implements IAuthorizatorPolicy {

    private static final String TOPIC_PREFIX = "iot";
    private final IIotDeviceService deviceService;

    /**
     * 校验设备向指定 Topic 发布消息的权限：仅允许写入「自身租户 + 自身 deviceCode」的 Topic。
     *
     * @param topic 发布的目标 Topic
     * @param user  用户名（不参与身份判定，仅保留接口签名）
     * @param client 客户端标识（即 deviceCode，唯一身份来源）
     * @return 允许发布返回 true，否则 false
     */
    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        String topicStr = topic.toString();
        // 身份口径统一为 clientId（username 不可作为身份来源，否则可冒充他人设备）
        String deviceCode = client;
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
        // 设备级隔离（IOT-MQTT-03）：Topic 第 3 段必须等于自己，禁止向他人 deviceCode 的 Topic 发布
        String topicDeviceCode = extractDeviceCode(topicStr);
        if (topicDeviceCode == null || !deviceCode.equals(topicDeviceCode)) {
            log.warn("MQTT 写权限拒绝：Topic deviceCode 与 clientId 不一致，deviceCode={}, topicDevice={}, topic={}",
                deviceCode, topicDeviceCode, topicStr);
            return false;
        }
        return true;
    }

    /**
     * 校验设备订阅 Topic 的权限：仅允许订阅「自身租户 + 自身 deviceCode」的 Topic，
     * 且禁止通配符租户（+/#）与通配符设备段。
     *
     * @param topic 订阅的 Topic
     * @param user  用户名（不参与身份判定，仅保留接口签名）
     * @param client 客户端标识（即 deviceCode，唯一身份来源）
     * @return 允许订阅返回 true，否则 false
     */
    @Override
    public boolean canRead(Topic topic, String user, String client) {
        String topicStr = topic.toString();
        String deviceCode = client;
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
        // 设备级隔离（IOT-MQTT-03）：禁止 iot/{tenant}/# 或 iot/{tenant}/{他人}/# 这类越权订阅
        String topicDeviceCode = extractDeviceCode(topicStr);
        if (topicDeviceCode == null || isWildcardSegment(topicDeviceCode)) {
            log.warn("MQTT 订阅权限拒绝：Topic 设备段缺失或为通配符，deviceCode={}, topic={}", deviceCode, topicStr);
            return false;
        }
        if (!deviceCode.equals(topicDeviceCode)) {
            log.warn("MQTT 订阅权限拒绝：Topic deviceCode 与 clientId 不一致，deviceCode={}, topicDevice={}, topic={}",
                deviceCode, topicDeviceCode, topicStr);
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
     * 从 Topic 中提取设备编码段（segment 2）。
     * <p>
     * 订阅场景下该段可能是通配符（{@code +} / {@code #}），由调用方显式拒绝。
     *
     * @return deviceCode 段，Topic 段数不足或前缀非法返回 null
     */
    private String extractDeviceCode(String topic) {
        if (StringUtils.isBlank(topic)) {
            return null;
        }
        String[] seg = topic.split("/");
        if (seg.length < 3 || !TOPIC_PREFIX.equals(seg[0])) {
            return null;
        }
        return seg[2];
    }

    /**
     * 判断 Topic 段是否为 MQTT 通配符（含 {@code +} 或 {@code #}）。
     */
    private boolean isWildcardSegment(String segment) {
        return segment.contains("+") || segment.contains("#");
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
