package com.nexus.iot.pipeline.payload;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * MQTT Topic 解析器。
 * <p>
 * Topic 规约（IoT 文档 16.2）：{@code iot/{tenantId}/{deviceCode}/{bizType}/{action}}
 * <ul>
 *   <li>segment 0：固定前缀 {@code iot}</li>
 *   <li>segment 1：tenantId（路由提示，权威值以设备记录为准）</li>
 *   <li>segment 2：deviceCode（提示，权威值以 MQTT ClientID 为准）</li>
 *   <li>segment 3：bizType — {@code property} / {@code event}</li>
 *   <li>segment 4：action — {@code post} / {@code set}</li>
 * </ul>
 * 非法 Topic 返回 {@code null}，由上层跳过。
 *
 * @author nexus
 */
@Component
public class IotMqttTopicParser {

    private static final String TOPIC_PREFIX = "iot";
    private static final Set<String> BIZ_TYPES = Set.of("property", "event");
    private static final Set<String> ACTIONS = Set.of("post", "set");

    /**
     * 解析 Topic。
     *
     * @param topic MQTT Topic
     * @return 解析结果；非法返回 null
     */
    public IotTopicInfo parse(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        String[] seg = topic.split("/");
        if (seg.length != 5 || !TOPIC_PREFIX.equals(seg[0])) {
            return null;
        }
        String tenantId = seg[1];
        String deviceCode = seg[2];
        String bizType = seg[3];
        String action = seg[4];
        if (tenantId.isBlank() || deviceCode.isBlank()
            || !BIZ_TYPES.contains(bizType) || !ACTIONS.contains(action)) {
            return null;
        }
        IotTopicInfo info = new IotTopicInfo();
        info.setTenantId(tenantId);
        info.setDeviceCode(deviceCode);
        info.setBizType(bizType);
        info.setAction(action);
        return info;
    }

    /**
     * Topic 解析结果。
     */
    @Data
    public static class IotTopicInfo {
        /**
         * 租户ID（路由提示）
         */
        private String tenantId;
        /**
         * 设备编码（提示，权威值以 ClientID 为准）
         */
        private String deviceCode;
        /**
         * 业务类型：property / event
         */
        private String bizType;
        /**
         * 动作：post / set
         */
        private String action;
    }

}
