package com.nexus.iot.pipeline;

import com.nexus.common.redis.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IoT 设备"最后数据上报时间"缓存。
 * <p>
 * Key 规约：{@code iot:lastreport:{deviceId}} — String，epoch 毫秒，TTL 7 天。
 * <p>
 * 为何不落 {@code iot_device} 列：设备上报频率可达 Hz 级，逐条 UPDATE 设备表会造成写放大与行锁竞争；
 * 且主脚本 {@code iot_device} 的存量 INSERT 为无列名位序写法，新增列会使其全部失效。
 * 因此改为"消费端节流写 Redis（同一设备 30s 最多一次）+ 运维详情端点聚合读取"。
 * <p>
 * 缓存异常一律降级（返回 null / 跳过写入），不影响数据管道主流程；同时使单测可直接 mock 本组件，
 * 无需静态打桩 {@code RedisUtils}（该类静态初始化依赖 Spring 容器）。
 *
 * @author nexus
 */
@Slf4j
@Component
public class IotDeviceReportTimeCache {

    /** 最后上报时间 Key 前缀 */
    private static final String KEY_PREFIX = "iot:lastreport:";
    /** 同设备写入节流窗口（毫秒），避免高频上报打爆 Redis 与网络往返 */
    private static final long THROTTLE_MS = 30_000L;
    /** 缓存保留时长：7 天，超过则视为"长期未上报" */
    private static final Duration TTL = Duration.ofDays(7);

    /** 设备 → 上次写缓存时间戳（内存节流表，规模与设备数同阶） */
    private final Map<Long, Long> lastWriteAt = new ConcurrentHashMap<>();

    /**
     * 记录设备最后上报时间（按设备节流，30s 内重复调用只更新内存时间不写 Redis）。
     *
     * @param deviceId   设备ID
     * @param reportTime 上报时间（服务端接收时间优先）
     */
    public void touch(Long deviceId, Date reportTime) {
        if (deviceId == null || reportTime == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastWriteAt.get(deviceId);
        if (last != null && now - last < THROTTLE_MS) {
            return;
        }
        lastWriteAt.put(deviceId, now);
        try {
            RedisUtils.setCacheObject(KEY_PREFIX + deviceId, String.valueOf(reportTime.getTime()), TTL);
        } catch (Exception e) {
            log.warn("写入最后上报时间缓存失败：deviceId={}, err={}", deviceId, e.getMessage());
        }
    }

    /**
     * 读取设备最后上报时间。
     *
     * @param deviceId 设备ID
     * @return 最后上报时间；无缓存/缓存异常返回 null
     */
    public Date get(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        try {
            String value = RedisUtils.getCacheObject(KEY_PREFIX + deviceId);
            return value == null ? null : new Date(Long.parseLong(value));
        } catch (Exception e) {
            log.warn("读取最后上报时间缓存失败：deviceId={}, err={}", deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 清空内存节流表（供单测复用同一实例时隔离用例）。
     */
    public void resetThrottle() {
        lastWriteAt.clear();
    }
}
