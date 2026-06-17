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
 *   <li>{@code iot:latest:{deviceId}} — Hash，设备最新属性值（Week 34 数据管道写入，见 {@code IotDeviceLatestCache}）</li>
 *   <li>{@code iot:will:{deviceId}} — String，遗嘱消息 payload，TTL 同 status（便于排查异常断开）</li>
 * </ul>
 *
 * @author nexus
 */
@Component
public class IotDeviceStatusCache {

    private static final String STATUS_KEY_PREFIX = "iot:status:";
    private static final String WILL_KEY_PREFIX = "iot:will:";

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

    /**
     * 读取设备在线状态。
     *
     * @param deviceId 设备ID
     * @return ONLINE / OFFLINE（无缓存视为 OFFLINE）/ UNKNOWN（deviceId 为空）
     */
    public String get(Long deviceId) {
        if (deviceId == null) {
            return "UNKNOWN";
        }
        String status = RedisUtils.getCacheObject(STATUS_KEY_PREFIX + deviceId);
        return status == null ? "OFFLINE" : status;
    }

    /**
     * 记录设备遗嘱消息（连接时声明，TTL 同在线状态）。
     *
     * @param deviceId 设备ID
     * @param willPayload 遗嘱 payload（可能为 null）
     */
    public void storeWill(Long deviceId, String willPayload) {
        if (deviceId == null || willPayload == null) {
            return;
        }
        RedisUtils.setCacheObject(WILL_KEY_PREFIX + deviceId, willPayload, Duration.ofHours(1));
    }

    /**
     * 清除设备遗嘱消息（断开连接时）。
     */
    public void clearWill(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        RedisUtils.deleteObject(WILL_KEY_PREFIX + deviceId);
    }

}
