package com.nexus.iot.pipeline;

import com.nexus.common.redis.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * IoT 告警运行态存储（Redis 访问的唯一出口）。
 * <p>
 * 把告警链路里所有 Redis 访问收敛到一个可替换的组件，原因有二：
 * <ol>
 *   <li><b>可测性</b>：{@link RedisUtils} 的静态初始化依赖 Spring 容器，单元测试无法对其静态打桩；</li>
 *   <li><b>降级一致</b>：Redis 不可用时统一返回"未命中 / 视为可推送"并记 debug，
 *       调用方无需各自 try/catch，告警链路不会因缓存故障中断（退化为"立即触发 + 不去抖"）。</li>
 * </ol>
 * 存储内容：
 * <ul>
 *   <li>{@code iot:alert:first:*}：持续时长计时起点（毫秒字符串）；</li>
 *   <li>{@code iot:alert:debounce:*}：去抖窗口占用标记；</li>
 *   <li>{@code iot:alert:devprod:*}：设备→产品缓存（产品级规则匹配用，避免每点查库）。</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@Component
public class IotAlertStateStore {

    /** 持续时长计时键前缀 */
    public static final String DURATION_KEY_PREFIX = "iot:alert:first:";
    /** 去抖窗口键前缀 */
    public static final String DEBOUNCE_KEY_PREFIX = "iot:alert:debounce:";
    /** 设备→产品缓存键前缀 */
    public static final String DEVICE_PRODUCT_KEY_PREFIX = "iot:alert:devprod:";

    /**
     * 持续时长计时键：同一 (租户, 设备, 属性, 规则) 维度。
     *
     * @param tenantId     租户ID
     * @param deviceId     设备ID
     * @param propertyCode 属性编码
     * @param ruleKey      规则键（规则ID 或 "default"）
     * @return Redis key
     */
    public static String durationKey(String tenantId, Long deviceId, String propertyCode, String ruleKey) {
        return DURATION_KEY_PREFIX + tenantId + ":" + deviceId + ":" + propertyCode + ":" + ruleKey;
    }

    /**
     * 去抖窗口键：同一 (租户, 设备, 属性, 规则) 维度。
     *
     * @param tenantId     租户ID
     * @param deviceId     设备ID
     * @param propertyCode 属性编码
     * @param ruleKey      规则键（规则ID 或 "default"）
     * @return Redis key
     */
    public static String debounceKey(String tenantId, Long deviceId, String propertyCode, String ruleKey) {
        return DEBOUNCE_KEY_PREFIX + tenantId + ":" + deviceId + ":" + propertyCode + ":" + ruleKey;
    }

    /**
     * 设备→产品缓存键。
     *
     * @param tenantId   租户ID
     * @param deviceCode 设备编码
     * @return Redis key
     */
    public static String deviceProductKey(String tenantId, String deviceCode) {
        return DEVICE_PRODUCT_KEY_PREFIX + tenantId + ":" + deviceCode;
    }

    /**
     * 读取持续时长计时起点。
     *
     * @param key 计时键
     * @return 毫秒字符串，未命中/Redis 不可用返回 null
     */
    public String readDurationStart(String key) {
        try {
            return RedisUtils.getCacheObject(key);
        } catch (Exception e) {
            log.debug("读取持续时长计时键失败：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写入/刷新持续时长计时起点。
     *
     * @param key     计时键
     * @param startMs 起点毫秒字符串
     * @param ttl     过期时间
     */
    public void writeDurationStart(String key, String startMs, Duration ttl) {
        try {
            RedisUtils.setCacheObject(key, startMs, ttl);
        } catch (Exception e) {
            log.debug("写入持续时长计时键失败：key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 清理持续时长计时键（恢复后重新计时）。
     *
     * @param key 计时键
     */
    public void clearDuration(String key) {
        try {
            RedisUtils.deleteObject(key);
        } catch (Exception e) {
            log.debug("清理持续时长计时键失败：key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 尝试占用去抖窗口。
     *
     * @param key 去抖键
     * @param ttl 窗口时长
     * @return true 表示本次可推送；false 表示窗口内已被占用；Redis 不可用时返回 true（不抑制推送）
     */
    public boolean acquireDebounce(String key, Duration ttl) {
        try {
            return RedisUtils.setObjectIfAbsent(key, "1", ttl);
        } catch (Exception e) {
            log.debug("去抖窗口占用失败，按可推送处理：key={}, err={}", key, e.getMessage());
            return true;
        }
    }

    /**
     * 读取设备→产品缓存。
     *
     * @param key 缓存键
     * @return 产品ID，未命中/Redis 不可用返回 null
     */
    public Long readDeviceProduct(String key) {
        try {
            return RedisUtils.getCacheObject(key);
        } catch (Exception e) {
            log.debug("读取设备产品缓存失败：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写入设备→产品缓存。
     *
     * @param key       缓存键
     * @param productId 产品ID
     * @param ttl       过期时间
     */
    public void writeDeviceProduct(String key, Long productId, Duration ttl) {
        try {
            RedisUtils.setCacheObject(key, productId, ttl);
        } catch (Exception e) {
            log.debug("写入设备产品缓存失败：key={}, err={}", key, e.getMessage());
        }
    }

}
