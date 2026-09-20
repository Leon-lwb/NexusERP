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
import java.util.concurrent.atomic.AtomicLong;

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
 * 安全与校验（IOT-MQTT-01 / IOT-PIPE-04）：
 * <ul>
 *   <li>身份口径统一为 MQTT {@code clientId}：Topic 中解析出的 deviceCode 与 clientId 不一致时直接丢弃并计数；</li>
 *   <li>{@code collect_time} 合理性校验：设备时间戳超出允许偏差时改用服务端时间，避免未来时间戳绕过 90 天清理。</li>
 * </ul>
 * <p>
 * 容错：解析 / XADD 任意异常均落日志后 return，不抛出，避免拖垮 Broker 会话线程。
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotMessagePipelineService {

    /** MQTT Topic 解析器，提取租户/设备/业务类型/动作。 */
    private final IotMqttTopicParser topicParser;
    /** 管道配置属性，提供 Stream Key 与时间戳偏差阈值。 */
    private final IotPipelineProperties properties;

    /**
     * 因 Topic deviceCode 与 clientId 不一致而被丢弃的消息计数（疑似越权发布/身份伪造）。
     */
    private final AtomicLong deviceMismatchDroppedCount = new AtomicLong();

    /**
     * 因设备时间戳超出合理范围而被纠正为服务端时间的消息计数。
     */
    private final AtomicLong collectTimeCorrectedCount = new AtomicLong();

    /**
     * 被丢弃消息计数（Topic deviceCode ≠ clientId）。
     *
     * @return 累计丢弃条数
     */
    public long getDeviceMismatchDroppedCount() {
        return deviceMismatchDroppedCount.get();
    }

    /**
     * 时间戳被纠正计数（设备时间戳超出合理范围）。
     *
     * @return 累计纠正条数
     */
    public long getCollectTimeCorrectedCount() {
        return collectTimeCorrectedCount.get();
    }

    /**
     * 处理一条 MQTT 属性上报消息，写入 Redis Stream。
     *
     * @param clientId MQTT ClientID（即设备编码，唯一身份口径）
     * @param topic    MQTT Topic
     * @param payload  消息负载（JSON）
     */
    public void publishToStream(String clientId, String topic, String payload) {
        IotMqttTopicParser.IotTopicInfo topicInfo = topicParser.parse(topic);
        if (topicInfo == null) {
            log.debug("跳过非规约 Topic：clientId={}, topic={}", clientId, topic);
            return;
        }
        // 身份口径统一为 clientId：Topic 第 3 段（deviceCode）必须与 MQTT ClientID 一致，否则直接丢弃
        if (StringUtils.isBlank(clientId) || !clientId.equals(topicInfo.getDeviceCode())) {
            long dropped = deviceMismatchDroppedCount.incrementAndGet();
            log.warn("MQTT 消息丢弃：Topic deviceCode 与 clientId 不一致（疑似越权发布/身份伪造），"
                    + "clientId={}, topicDeviceCode={}, topic={}, 累计丢弃={}",
                clientId, topicInfo.getDeviceCode(), topic, dropped);
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
        Date collectTime = resolveCollectTime(pl.getTimestamp(), now.getTime(), clientId);
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

    /**
     * 校验设备上报时间戳的合理性（IOT-PIPE-04）。
     * <p>
     * 设备时钟错误、固件 bug 或恶意设备可能上报未来时间戳（如 2100 年）：
     * 该数据永远不满足 {@code collect_time < now-90d} 的清理条件，会永久占用存储，
     * 并在 {@code order by collect_time desc} 的查询里永久霸占「最新数据」首位。
     * 因此设备时间戳超出 {@code [now - pastTolerance, now + futureTolerance]} 时改用服务端时间并告警。
     *
     * @param deviceTimestamp 设备上报的毫秒时间戳（可空）
     * @param nowMs           服务端当前毫秒时间
     * @param deviceCode      设备编码（用于日志定位坏设备）
     * @return 最终采用的采集时间（合理则用设备时间，否则用服务端时间）
     */
    public Date resolveCollectTime(Long deviceTimestamp, long nowMs, String deviceCode) {
        if (deviceTimestamp == null) {
            return new Date(nowMs);
        }
        long futureTolerance = properties.getCollectTimeFutureToleranceMs();
        long pastTolerance = properties.getCollectTimePastToleranceMs();
        if (deviceTimestamp > nowMs + futureTolerance || deviceTimestamp < nowMs - pastTolerance) {
            long corrected = collectTimeCorrectedCount.incrementAndGet();
            log.warn("设备时间戳超出合理范围，已改用服务端时间：deviceCode={}, deviceTimestamp={}, driftMs={}, "
                    + "允许范围=[-{}ms, +{}ms], 累计纠正={}",
                deviceCode, deviceTimestamp, deviceTimestamp - nowMs,
                pastTolerance, futureTolerance, corrected);
            return new Date(nowMs);
        }
        return new Date(deviceTimestamp);
    }

}
