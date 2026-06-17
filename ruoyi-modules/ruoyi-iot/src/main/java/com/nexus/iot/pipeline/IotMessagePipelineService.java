package com.nexus.iot.pipeline;

import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.iot.pipeline.payload.IotMqttTopicParser;
import com.nexus.iot.pipeline.payload.IotPropertyPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * IoT 消息管道服务：MQTT onPublish → Redis Stream XADD。
 * <p>
 * Week 34 生产端职责：
 * <ol>
 *   <li>解析 Topic（仅处理 {@code property/post}，{@code event/post} 暂仅日志）；</li>
 *   <li>解析 JSON 负载；</li>
 *   <li>组装 Stream entry（字段对齐 IoT 文档 16.4）并 {@code XADD}。</li>
 * </ol>
 * 设备ID / 权威 tenantId 的 DB 解析下沉到消费端批量处理，保持 Broker 会话线程只做 Redis 写。
 * <p>
 * 容错：解析 / XADD 任意异常均落日志后 return，不抛出，避免拖垮 Broker 会话线程。
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotMessagePipelineService {

    private final IotMqttTopicParser topicParser;
    private final IotPipelineProperties properties;

    /**
     * 处理一条 MQTT 属性上报消息，写入 Redis Stream。
     *
     * @param clientId MQTT ClientID（即设备编码）
     * @param topic    MQTT Topic
     * @param payload  消息负载（JSON）
     */
    public void publishToStream(String clientId, String topic, String payload) {
        IotMqttTopicParser.IotTopicInfo topicInfo = topicParser.parse(topic);
        if (topicInfo == null) {
            log.debug("跳过非规约 Topic：clientId={}, topic={}", clientId, topic);
            return;
        }
        // 仅处理属性上报；事件上报本周仅日志
        if (!"property".equals(topicInfo.getBizType()) || !"post".equals(topicInfo.getAction())) {
            log.debug("暂不处理的 Topic 类型：clientId={}, topic={}", clientId, topic);
            return;
        }
        IotPropertyPayload pl;
        try {
            pl = JsonUtils.parseObject(payload, IotPropertyPayload.class);
        } catch (Exception e) {
            log.warn("MQTT 负载解析失败，已丢弃：clientId={}, topic={}, payload={}", clientId, topic, payload, e);
            return;
        }
        if (pl == null || StringUtils.isBlank(pl.getProperty()) || pl.getValue() == null) {
            log.warn("MQTT 负载缺少必填字段(property/value)，已丢弃：clientId={}, payload={}", clientId, payload);
            return;
        }

        Date now = new Date();
        Date collectTime = pl.getTimestamp() != null ? new Date(pl.getTimestamp()) : now;
        String quality = StringUtils.isNotBlank(pl.getQuality()) ? pl.getQuality() : "GOOD";

        Map<Object, Object> entries = new HashMap<>();
        entries.put("device_code", clientId);
        entries.put("topic_tenant_id", topicInfo.getTenantId());
        entries.put("property_code", pl.getProperty());
        entries.put("data_type", pl.getDataType() == null ? "" : pl.getDataType());
        entries.put("value", String.valueOf(pl.getValue()));
        entries.put("unit", pl.getUnit() == null ? "" : pl.getUnit());
        entries.put("collect_time", String.valueOf(collectTime.getTime()));
        entries.put("receive_time", String.valueOf(now.getTime()));
        entries.put("quality", quality);
        entries.put("source", "MQTT");
        try {
            RStream<Object, Object> stream = RedisUtils.getClient().getStream(properties.getStreamKey());
            stream.add(StreamAddArgs.entries(entries));
            if (log.isDebugEnabled()) {
                log.debug("XADD 设备数据：deviceCode={}, property={}, value={}", clientId, pl.getProperty(), pl.getValue());
            }
        } catch (Exception e) {
            log.error("XADD Redis Stream 失败：clientId={}, topic={}, payload={}", clientId, topic, payload, e);
        }
    }

}
