package com.nexus.iot.pipeline;

import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.iot.device.domain.IotDeviceData;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * IoT 设备最新属性值 Redis 缓存。
 * <p>
 * Key 规约（IoT 文档 16.5）：{@code iot:latest:{deviceId}} — Hash，propertyCode → value（字符串），
 * 无 TTL（覆盖更新）。由 StreamConsumer 写入，供 SSE 实时推送（Week 35）与 MES 查询读取。
 *
 * @author nexus
 */
@Component
public class IotDeviceLatestCache {

    private static final String LATEST_KEY_PREFIX = "iot:latest:";

    /**
     * 更新设备某属性的最新值。
     *
     * @param data 已落库的设备数据（含 deviceId / propertyCode / 各值列）
     */
    public void update(IotDeviceData data) {
        if (data == null || data.getDeviceId() == null || data.getPropertyCode() == null) {
            return;
        }
        String key = LATEST_KEY_PREFIX + data.getDeviceId();
        RedisUtils.setCacheMapValue(key, data.getPropertyCode(), serialize(data));
    }

    /**
     * 读取设备全部最新属性值。
     *
     * @param deviceId 设备ID
     * @return propertyCode → value 字符串
     */
    public Map<String, String> getAll(Long deviceId) {
        if (deviceId == null) {
            return Map.of();
        }
        Map<String, String> map = RedisUtils.getCacheMap(LATEST_KEY_PREFIX + deviceId);
        return map == null ? Map.of() : map;
    }

    /**
     * 读取设备单个属性最新值。
     */
    public String get(Long deviceId, String propertyCode) {
        if (deviceId == null || propertyCode == null) {
            return null;
        }
        return RedisUtils.getCacheMapValue(LATEST_KEY_PREFIX + deviceId, propertyCode);
    }

    /**
     * 将数据实体的值序列化为缓存字符串：优先 numericValue，其次 boolValue，最后 textValue。
     */
    private String serialize(IotDeviceData data) {
        if (data.getNumericValue() != null) {
            return data.getNumericValue().toPlainString();
        }
        if (data.getBoolValue() != null) {
            return data.getBoolValue().toString();
        }
        return data.getTextValue();
    }

}
