package com.nexus.iot.pipeline;

import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.service.IIotDeviceDataService;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.pipeline.payload.IotDataValueResolver;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IoT 设备数据 Redis Stream 消费者。
 * <p>
 * Week 34 数据管道消费端核心：
 * <ol>
 *   <li>{@code @PostConstruct} 幂等创建消费者组（BUSYGROUP 视为已存在），启动单线程消费循环；</li>
 *   <li>每轮先回收本消费者 pending（{@code >} 用 {@code StreamMessageId.MIN} 读已投递未 ACK），
 *       再拉取新消息（{@code StreamMessageId.NEWEST}），保证异常重启后不丢；</li>
 *   <li>批量落 {@code iot_device_data} → 更新 {@code iot:latest} 缓存 → 告警检查 → 批量 XACK；</li>
 *   <li>批量失败回退逐条插入以隔离毒消息；连续 {@code maxRetry} 次失败单条 ACK 跳过并记 ERROR；</li>
 *   <li>{@code @PreDestroy} 优雅停止消费线程。</li>
 * </ol>
 * 仅在 {@code nexus.iot.pipeline.consumer-enabled=true}（默认）时启动。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(IotPipelineProperties.class)
@ConditionalOnProperty(prefix = "nexus.iot.pipeline", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class IotStreamConsumer {

    private final IotPipelineProperties properties;
    private final IIotDeviceService deviceService;
    private final IIotDeviceDataService deviceDataService;
    private final IotDeviceLatestCache latestCache;
    private final IotAlertChecker alertChecker;
    private final IotDataValueResolver valueResolver;

    private RStream<Object, Object> stream;
    private ExecutorService executor;
    private volatile boolean running = false;

    /**
     * 毒消息重试计数：messageId → 已失败次数
     */
    private final Map<StreamMessageId, Integer> retryCount = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        stream = RedisUtils.getClient().getStream(properties.getStreamKey());
        createGroupIfNeeded();
        running = true;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "iot-stream-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::consumeLoop);
        log.info("IoT StreamConsumer 已启动：stream={}, group={}, consumer={}",
            properties.getStreamKey(), properties.getConsumerGroup(), properties.getConsumerName());
    }

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
        log.info("IoT StreamConsumer 已停止");
    }

    /**
     * 幂等创建消费者组（起始 ID = NEWEST，仅消费建组后的新消息；MKSTREAM 自动建流）。
     */
    private void createGroupIfNeeded() {
        try {
            stream.createGroup(StreamCreateGroupArgs.name(properties.getConsumerGroup())
                .id(StreamMessageId.NEWEST).makeStream());
            log.info("消费者组已创建：{}", properties.getConsumerGroup());
        } catch (Exception e) {
            // BUSYGROUP 等已存在情况，幂等忽略
            log.info("消费者组已存在或创建忽略：{}", e.getMessage());
        }
    }

    /**
     * 消费主循环：先回收 pending，再拉取新消息。
     */
    private void consumeLoop() {
        // pending：id=0（StreamMessageId.ALL）回收本消费者已投递未 ACK 的消息，保证异常重启不丢
        StreamReadGroupArgs pendingArgs = StreamReadGroupArgs.greaterThan(StreamMessageId.ALL)
            .count(properties.getBatchSize()).timeout(Duration.ofMillis(100));
        // fresh：id=">"（neverDelivered）拉取从未投递的新消息，BLOCK 等待
        StreamReadGroupArgs freshArgs = StreamReadGroupArgs.neverDelivered()
            .count(properties.getBatchSize()).timeout(Duration.ofMillis(properties.getBlockMs()));

        while (running) {
            try {
                // 1. 优先回收本消费者 pending（异常重启后未 ACK 的消息）
                Map<StreamMessageId, Map<Object, Object>> messages = stream.readGroup(
                    properties.getConsumerGroup(), properties.getConsumerName(), pendingArgs);
                // 2. 无 pending 则拉取新消息（BLOCK）
                if (messages == null || messages.isEmpty()) {
                    messages = stream.readGroup(
                        properties.getConsumerGroup(), properties.getConsumerName(), freshArgs);
                }
                if (messages == null || messages.isEmpty()) {
                    continue;
                }
                processBatch(messages);
            } catch (Exception e) {
                if (running) {
                    log.error("StreamConsumer 读取异常，稍后重试", e);
                    sleepQuiet(500);
                }
            }
        }
        log.info("StreamConsumer 消费循环退出");
    }

    /**
     * 处理一批消息。
     */
    private void processBatch(Map<StreamMessageId, Map<Object, Object>> messages) {
        // 1. 按设备编码批量解析设备（每个 deviceCode 一次 DB 查询）
        Set<String> deviceCodes = new java.util.HashSet<>();
        for (Map<Object, Object> entry : messages.values()) {
            String code = str(entry.get("device_code"));
            if (StringUtils.isNotBlank(code)) {
                deviceCodes.add(code);
            }
        }
        Map<String, IotDevice> deviceMap = new HashMap<>();
        for (String code : deviceCodes) {
            try {
                IotDevice device = deviceService.selectEntityByCode(code);
                if (device != null) {
                    deviceMap.put(code, device);
                }
            } catch (Exception e) {
                log.error("查询设备失败：deviceCode={}", code, e);
            }
        }

        // 2. 组装实体
        List<IotDeviceData> entities = new ArrayList<>();
        List<StreamMessageId> ids = new ArrayList<>();
        for (Map.Entry<StreamMessageId, Map<Object, Object>> e : messages.entrySet()) {
            Map<Object, Object> entry = e.getValue();
            String code = str(entry.get("device_code"));
            IotDevice device = deviceMap.get(code);
            if (device == null) {
                log.warn("设备不存在，跳过消息：deviceCode={}", code);
                ack(e.getKey());
                continue;
            }
            IotDeviceData data = buildData(entry, device);
            entities.add(data);
            ids.add(e.getKey());
        }
        if (entities.isEmpty()) {
            return;
        }

        // 3. 批量落库；失败则回退逐条插入以隔离毒消息
        boolean batchOk = false;
        try {
            deviceDataService.batchInsert(entities);
            batchOk = true;
        } catch (Exception e) {
            log.warn("批量落库失败，回退逐条插入：{}", e.getMessage());
        }
        if (batchOk) {
            for (int i = 0; i < entities.size(); i++) {
                afterPersist(entities.get(i));
                ack(ids.get(i));
            }
        } else {
            for (int i = 0; i < entities.size(); i++) {
                StreamMessageId id = ids.get(i);
                IotDeviceData data = entities.get(i);
                try {
                    deviceDataService.insert(data);
                    afterPersist(data);
                    ack(id);
                    retryCount.remove(id);
                } catch (Exception ex) {
                    handlePoison(id, data, ex);
                }
            }
        }
    }

    /**
     * 落库后处理：更新最新值缓存 + 告警检查。
     */
    private void afterPersist(IotDeviceData data) {
        try {
            latestCache.update(data);
        } catch (Exception e) {
            log.warn("更新最新值缓存失败：device={}, property={}", data.getDeviceCode(), data.getPropertyCode(), e);
        }
        try {
            alertChecker.check(data);
        } catch (Exception e) {
            log.warn("告警检查异常：device={}, property={}", data.getDeviceCode(), data.getPropertyCode(), e);
        }
    }

    /**
     * 毒消息处理：累计失败达 maxRetry 则 ACK 跳过并记 ERROR，否则保留 pending 下轮重试。
     */
    private void handlePoison(StreamMessageId id, IotDeviceData data, Exception ex) {
        int retry = retryCount.merge(id, 1, Integer::sum);
        if (retry >= properties.getMaxRetry()) {
            log.error("毒消息重试 {} 次仍失败，ACK 跳过：device={}, property={}, value={}, err={}",
                retry, data.getDeviceCode(), data.getPropertyCode(),
                data.getNumericValue() != null ? data.getNumericValue() : data.getTextValue(), ex.getMessage());
            ack(id);
            retryCount.remove(id);
        } else {
            log.warn("消息处理失败，第 {} 次重试（保留 pending）：device={}, property={}",
                retry, data.getDeviceCode(), data.getPropertyCode());
        }
    }

    private void ack(StreamMessageId id) {
        try {
            stream.ack(properties.getConsumerGroup(), id);
        } catch (Exception e) {
            log.warn("XACK 失败：id={}, err={}", id, e.getMessage());
        }
    }

    /**
     * 从 Stream entry 与设备记录组装 {@link IotDeviceData}。
     * 权威 tenantId / deviceId 取自设备记录；Topic 中的 tenantId 仅作交叉校验（决策 G）。
     */
    private IotDeviceData buildData(Map<Object, Object> entry, IotDevice device) {
        IotDeviceData d = new IotDeviceData();
        d.setTenantId(device.getTenantId());
        d.setDeviceId(device.getDeviceId());
        d.setDeviceCode(device.getDeviceCode());
        d.setPropertyCode(str(entry.get("property_code")));
        String dataType = str(entry.get("data_type"));
        d.setDataType(StringUtils.isBlank(dataType) ? "NUMERIC" : dataType);
        d.setQuality(strOrEmpty(entry.get("quality")));
        d.setSource(strOrEmpty(entry.get("source")));
        d.setCollectTime(toDate(entry.get("collect_time")));
        d.setReceiveTime(toDate(entry.get("receive_time")));
        d.setCreateTime(new Date());

        // tenantId 交叉校验：Topic 声称的租户与设备记录不一致时以设备为准并告警
        String topicTenant = str(entry.get("topic_tenant_id"));
        if (StringUtils.isNotBlank(topicTenant) && !topicTenant.equals(device.getTenantId())) {
            log.warn("Topic tenantId 与设备记录不一致，以设备为准：deviceCode={}, topicTenant={}, deviceTenant={}",
                device.getDeviceCode(), topicTenant, device.getTenantId());
        }

        valueResolver.applyValue(d, entry.get("value"), d.getDataType());
        return d;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String strOrEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private Date toDate(Object o) {
        if (o == null) {
            return new Date();
        }
        try {
            return new Date(Long.parseLong(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return new Date();
        }
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
