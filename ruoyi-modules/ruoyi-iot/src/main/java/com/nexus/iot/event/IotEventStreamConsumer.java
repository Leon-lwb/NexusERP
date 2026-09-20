package com.nexus.iot.event;

import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.event.service.IIotDeviceEventService;
import com.nexus.iot.pipeline.IotPipelineProperties;
import com.nexus.iot.command.service.IIotDeviceCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * IoT 事件流消费者（IOT-PIPE-02）。
 * <p>
 * 消费 {@code iot:event:stream}（由 {@code IotMessagePipelineService} 从
 * {@code event/post} 与 {@code property/set_reply} 两个 Topic 生产）：
 * <ol>
 *   <li>{@code entry_type=EVENT} → {@link IIotDeviceEventService#persistEvent} 落 {@code iot_device_event}（幂等）；</li>
 *   <li>{@code entry_type=COMMAND_REPLY} → {@link IIotDeviceCommandService#handleReply} 更新下行指令状态与回执。</li>
 * </ol>
 * 与数据流消费者（{@code IotStreamConsumer}）结构一致：先回收 pending 再拉新、租户交叉校验、
 * 毒消息重试上限后 ACK 跳过；独立消费者组，互不影响。
 * <p>
 * 仅在 {@code nexus.iot.pipeline.event-consumer-enabled=true}（默认）时启动。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(IotPipelineProperties.class)
@ConditionalOnProperty(prefix = "nexus.iot.pipeline", name = "event-consumer-enabled", havingValue = "true", matchIfMissing = true)
public class IotEventStreamConsumer {

    private final IotPipelineProperties properties;
    private final IIotDeviceService deviceService;
    private final IIotDeviceEventService deviceEventService;
    private final IIotDeviceCommandService deviceCommandService;
    /**
     * OTA 升级任务服务（旁路联动）：{@code OTA_*} 事件同步更新 {@code iot_ota_task} 的进度/结果。
     * <p>仅对事件码以 {@code OTA_} 开头的事件生效，其余事件直接返回；实现内部吞异常，
     * 不影响事件落库与消费循环。
     */
    private final com.nexus.iot.ota.service.IIotOtaTaskService deviceOtaTaskService;

    private RStream<Object, Object> stream;
    private ExecutorService executor;
    private volatile boolean running = false;

    /**
     * 毒消息重试计数：messageId → 已失败次数
     */
    private final Map<StreamMessageId, Integer> retryCount = new ConcurrentHashMap<>();

    /**
     * 初始化：绑定事件流、幂等创建消费者组并启动消费循环。
     */
    @PostConstruct
    public void init() {
        stream = RedisUtils.getClient().getStream(properties.getEventStreamKey());
        createGroupIfNeeded();
        running = true;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "iot-event-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::consumeLoop);
        log.info("IoT EventStreamConsumer 已启动：stream={}, group={}, consumer={}",
            properties.getEventStreamKey(), properties.getEventConsumerGroup(), properties.getEventConsumerName());
    }

    /**
     * 销毁：停止消费循环并优雅关闭线程。
     */
    @PreDestroy
    public void destroy() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("IoT EventStreamConsumer 已停止");
    }

    /**
     * 幂等创建消费者组（起始 ID = NEWEST）。
     */
    private void createGroupIfNeeded() {
        try {
            stream.createGroup(StreamCreateGroupArgs.name(properties.getEventConsumerGroup())
                .id(StreamMessageId.NEWEST).makeStream());
            log.info("事件流消费者组已创建：{}", properties.getEventConsumerGroup());
        } catch (Exception e) {
            log.info("事件流消费者组已存在或创建忽略：{}", e.getMessage());
        }
    }

    /**
     * 消费主循环：先回收 pending，再拉取新消息。
     */
    private void consumeLoop() {
        StreamReadGroupArgs pendingArgs = StreamReadGroupArgs.greaterThan(StreamMessageId.ALL)
            .count(properties.getBatchSize()).timeout(Duration.ofMillis(100));
        StreamReadGroupArgs freshArgs = StreamReadGroupArgs.neverDelivered()
            .count(properties.getBatchSize()).timeout(Duration.ofMillis(properties.getBlockMs()));
        while (running) {
            try {
                Map<StreamMessageId, Map<Object, Object>> messages = stream.readGroup(
                    properties.getEventConsumerGroup(), properties.getEventConsumerName(), pendingArgs);
                if (messages == null || messages.isEmpty()) {
                    messages = stream.readGroup(
                        properties.getEventConsumerGroup(), properties.getEventConsumerName(), freshArgs);
                }
                if (messages == null || messages.isEmpty()) {
                    continue;
                }
                processBatch(messages);
            } catch (Exception e) {
                if (running) {
                    log.error("EventStreamConsumer 读取异常，稍后重试", e);
                    sleepQuiet(500);
                }
            }
        }
        log.info("EventStreamConsumer 消费循环退出");
    }

    /**
     * 处理一批事件/回执消息。
     * <p>包级可见：便于单元测试在不启动 Redis 的前提下直接校验分派、租户拒绝与幂等分支。
     *
     * @param messages Stream 消息批次
     */
    void processBatch(Map<StreamMessageId, Map<Object, Object>> messages) {
        Map<String, IotDevice> deviceMap = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (Map<Object, Object> entry : messages.values()) {
            String code = str(entry.get("device_code"));
            if (StringUtils.isNotBlank(code)) {
                codes.add(code);
            }
        }
        for (String code : codes) {
            try {
                IotDevice device = deviceService.selectEntityByCode(code);
                if (device != null) {
                    deviceMap.put(code, device);
                }
            } catch (Exception e) {
                log.error("查询设备失败：deviceCode={}", code, e);
            }
        }
        for (Map.Entry<StreamMessageId, Map<Object, Object>> e : messages.entrySet()) {
            StreamMessageId id = e.getKey();
            Map<Object, Object> entry = e.getValue();
            String code = str(entry.get("device_code"));
            IotDevice device = deviceMap.get(code);
            if (device == null) {
                log.warn("设备不存在，跳过事件消息：deviceCode={}", code);
                ack(id);
                continue;
            }
            // 租户交叉校验：Topic 声称租户与设备记录不一致 → 拒绝处理并 ACK（防跨租户注入）
            String topicTenant = str(entry.get("topic_tenant_id"));
            if (StringUtils.isNotBlank(topicTenant) && !topicTenant.equals(device.getTenantId())) {
                log.warn("事件 Topic tenantId 与设备记录不一致，拒绝处理并 ACK：streamId={}, deviceCode={}, "
                        + "topicTenant={}, deviceTenant={}",
                    id, code, topicTenant, device.getTenantId());
                ack(id);
                continue;
            }
            try {
                if ("COMMAND_REPLY".equals(str(entry.get("entry_type")))) {
                    deviceCommandService.handleReply(device, entry, id.toString());
                } else {
                    deviceEventService.persistEvent(device, entry, id.toString());
                    // OTA 旁路（OTA MVP）：设备经 event/post 上报 OTA_PROGRESS/OTA_RESULT，
                    // 事件落库后同步推进升级任务状态机；内部吞异常，不阻断事件消费。
                    deviceOtaTaskService.handleOtaEvent(device, entry, id.toString());
                }
                retryCount.remove(id);
                ack(id);
            } catch (Exception ex) {
                handlePoison(id, entry, ex);
            }
        }
    }

    /**
     * 毒消息处理：累计失败达 maxRetry 则 ACK 跳过并记 ERROR，否则保留 pending 下轮重试。
     */
    private void handlePoison(StreamMessageId id, Map<Object, Object> entry, Exception ex) {
        int retry = retryCount.merge(id, 1, Integer::sum);
        if (retry >= properties.getMaxRetry()) {
            log.error("事件毒消息重试 {} 次仍失败，ACK 跳过：streamId={}, entry={}, err={}",
                retry, id, entry, ex.getMessage());
            ack(id);
            retryCount.remove(id);
        } else {
            log.warn("事件消息处理失败，第 {} 次重试（保留 pending）：streamId={}, err={}", retry, id, ex.getMessage());
        }
    }

    /**
     * 确认（ACK）已处理完成的消息，异常仅记日志不阻断。
     */
    private void ack(StreamMessageId id) {
        try {
            stream.ack(properties.getEventConsumerGroup(), id);
        } catch (Exception e) {
            log.warn("事件流 XACK 失败：id={}, err={}", id, e.getMessage());
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
