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
     * 因 Topic 类型暂不支持而被跳过的消息计数（不再静默丢弃，便于运维发现设备端使用了新 Topic）。
     */
    private final AtomicLong unsupportedTopicCount = new AtomicLong();

    /**
     * 已路由到事件流的设备事件计数（event/post）。
     */
    private final AtomicLong eventRoutedCount = new AtomicLong();

    /**
     * 已路由到事件流的指令回执计数（property/set_reply）。
     */
    private final AtomicLong replyRoutedCount = new AtomicLong();

    /**
     * 被跳过的 Topic 计数（暂不支持的 bizType/action 组合）。
     *
     * @return 累计跳过条数
     */
    public long getUnsupportedTopicCount() {
        return unsupportedTopicCount.get();
    }

    /**
     * 已路由的设备事件计数。
     *
     * @return 累计事件条数
     */
    public long getEventRoutedCount() {
        return eventRoutedCount.get();
    }

    /**
     * 已路由的指令回执计数。
     *
     * @return 累计回执条数
     */
    public long getReplyRoutedCount() {
        return replyRoutedCount.get();
    }

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
        String bizType = topicInfo.getBizType();
        String action = topicInfo.getAction();
        // IOT-PIPE-02：事件上报（event/post）与指令回执（property/set_reply）不再静默丢弃
        boolean isData = "property".equals(bizType) && IotMqttTopicParser.ACTION_POST.equals(action);
        boolean isEvent = "event".equals(bizType) && IotMqttTopicParser.ACTION_POST.equals(action);
        boolean isReply = "property".equals(bizType) && IotMqttTopicParser.ACTION_SET_REPLY.equals(action);
        if (!isData && !isEvent && !isReply) {
            long unsupported = unsupportedTopicCount.incrementAndGet();
            log.info("MQTT Topic 暂不处理（已计数，不再静默丢弃）：clientId={}, topic={}, bizType={}, action={}, 累计={}",
                clientId, topic, bizType, action, unsupported);
            return;
        }
        if (isData) {
            publishData(clientId, topicInfo, payload);
        } else if (isEvent) {
            publishEvent(clientId, topicInfo, payload);
        } else {
            publishCommandReply(clientId, topicInfo, payload);
        }
    }

    /**
     * 属性上报（{@code property/post}）→ 数据流（原有链路，逻辑保持不变）。
     */
    private void publishData(String clientId, IotMqttTopicParser.IotTopicInfo topicInfo, String payload) {
        IotPropertyPayload pl;
        try {
            pl = JsonUtils.parseObject(payload, IotPropertyPayload.class);
        } catch (Exception e) {
            log.warn("MQTT 负载解析失败，已丢弃：clientId={}, payload={}", clientId, payload, e);
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
        xadd(properties.getStreamKey(), entries);
        if (log.isDebugEnabled()) {
            log.debug("XADD 设备数据：deviceCode={}, property={}, value={}", clientId, pl.getProperty(), pl.getValue());
        }
    }

    /**
     * 设备事件上报（{@code event/post}）→ 事件流，由 {@code IotEventStreamConsumer} 落 {@code iot_device_event}。
     * <p>
     * 事件编码取值优先级：{@code eventCode} &gt; {@code event} &gt; {@code code} &gt; {@code property}，
     * 兼容主流设备固件的事件报文命名；事件时间戳复用 {@link #resolveCollectTime} 的合理性校验。
     */
    private void publishEvent(String clientId, IotMqttTopicParser.IotTopicInfo topicInfo, String payload) {
        Map<String, Object> map = parseMap(clientId, payload, "设备事件");
        if (map == null) {
            return;
        }
        String eventCode = firstNonBlank(str(map.get("eventCode")), str(map.get("event")),
            str(map.get("code")), str(map.get("property")));
        if (StringUtils.isBlank(eventCode)) {
            log.warn("设备事件缺少事件编码(eventCode/event/code)，已丢弃：clientId={}, payload={}", clientId, payload);
            return;
        }
        Date now = new Date();
        Date eventTime = resolveCollectTime(longOf(map.get("timestamp")), now.getTime(), clientId);
        Map<Object, Object> entries = new HashMap<>();
        entries.put("entry_type", "EVENT");
        entries.put("device_code", clientId);
        entries.put("topic_tenant_id", topicInfo.getTenantId());
        entries.put("event_code", eventCode);
        entries.put("event_name", firstNonBlank(str(map.get("eventName")), str(map.get("name"))));
        entries.put("event_level", firstNonBlank(str(map.get("eventLevel")), str(map.get("level"))));
        entries.put("event_time", String.valueOf(eventTime.getTime()));
        entries.put("receive_time", String.valueOf(now.getTime()));
        entries.put("payload", payload == null ? "" : payload);
        entries.put("quality", firstNonBlank(str(map.get("quality")), "GOOD"));
        entries.put("source", "MQTT");
        xadd(properties.getEventStreamKey(), entries);
        long routed = eventRoutedCount.incrementAndGet();
        log.info("XADD 设备事件：deviceCode={}, eventCode={}, level={}, 累计={}",
            clientId, eventCode, map.get("eventLevel"), routed);
    }

    /**
     * 指令回执（{@code property/set_reply}）→ 事件流（entry_type=COMMAND_REPLY），
     * 由消费端更新 {@code iot_device_command} 状态与回执载荷。
     */
    private void publishCommandReply(String clientId, IotMqttTopicParser.IotTopicInfo topicInfo, String payload) {
        Map<String, Object> map = parseMap(clientId, payload, "指令回执");
        if (map == null) {
            return;
        }
        Map<Object, Object> entries = new HashMap<>();
        entries.put("entry_type", "COMMAND_REPLY");
        entries.put("device_code", clientId);
        entries.put("topic_tenant_id", topicInfo.getTenantId());
        entries.put("command_id", firstNonBlank(str(map.get("commandId")), str(map.get("msgId")), str(map.get("id"))));
        entries.put("success", String.valueOf(isSuccess(map)));
        entries.put("message", firstNonBlank(str(map.get("message")), str(map.get("msg"))));
        entries.put("receive_time", String.valueOf(System.currentTimeMillis()));
        entries.put("payload", payload == null ? "" : payload);
        entries.put("source", "MQTT");
        xadd(properties.getEventStreamKey(), entries);
        long routed = replyRoutedCount.incrementAndGet();
        log.info("XADD 指令回执：deviceCode={}, commandId={}, success={}, 累计={}",
            clientId, entries.get("command_id"), entries.get("success"), routed);
    }

    /**
     * 解析 JSON 负载为 Map，失败返回 null（已记日志）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String clientId, String payload, String label) {
        try {
            Map<String, Object> map = JsonUtils.parseObject(payload, Map.class);
            if (map == null) {
                log.warn("{}负载为空，已丢弃：clientId={}", label, clientId);
            }
            return map;
        } catch (Exception e) {
            log.warn("{}负载解析失败，已丢弃：clientId={}, payload={}", label, clientId, payload, e);
            return null;
        }
    }

    /**
     * 判断回执是否表示执行成功：优先 {@code success}，其次 {@code code}（0/200 视为成功）。
     */
    private boolean isSuccess(Map<String, Object> map) {
        Object success = map.get("success");
        if (success instanceof Boolean b) {
            return b;
        }
        if (success != null) {
            String s = String.valueOf(success);
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        }
        Object code = map.get("code");
        if (code != null) {
            String s = String.valueOf(code);
            return "0".equals(s) || "200".equals(s);
        }
        // 未携带成功标志时按"已回执即成功"处理（失败原因见 message 字段）
        return true;
    }

    /**
     * 写入 Redis Stream，异常仅记日志（不向 Broker 会话线程抛出）。
     */
    private void xadd(String streamKey, Map<Object, Object> entries) {
        try {
            RStream<Object, Object> stream = RedisUtils.getClient().getStream(streamKey);
            stream.add(StreamAddArgs.entries(entries));
        } catch (Exception e) {
            log.error("XADD Redis Stream 失败：stream={}, entries={}", streamKey, entries, e);
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
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
