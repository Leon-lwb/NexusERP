package com.nexus.iot.mqtt;

import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.iot.device.domain.IotDevice;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * IoT 设备在线状态 Redis 缓存。
 * <p>
 * Key 规约（与 IoT 文档 16.5 一致）：
 * <ul>
 *   <li>{@code iot:status:{deviceId}} — String，ONLINE/OFFLINE，TTL 1 小时</li>
 *   <li>{@code iot:latest:{deviceId}} — Hash，设备最新属性值（Week 34 数据管道写入）</li>
 * </ul>
 *
 * @author nexus
 */
@Component
public class IotDeviceStatusCache {

    private static final String STATUS_KEY_PREFIX = "iot:status:";

    /**
     * 标记设备在线（TTL 1 小时，超时未续期则视为离线）
     */
    public void markOnline(IotDevice device) {
        if (device == null || device.getDeviceId() == null) {
            return;
        }
        RedisUtils.setCacheObject(STATUS_KEY_PREFIX + device.getDeviceId(), "ONLINE",
            Duration.ofHours(1));
    }

    /**
     * 标记设备离线（删除在线标记）
     */
    public void markOffline(IotDevice device) {
        if (device == null || device.getDeviceId() == null) {
            return;
        }
        RedisUtils.deleteObject(STATUS_KEY_PREFIX + device.getDeviceId());
    }
}
