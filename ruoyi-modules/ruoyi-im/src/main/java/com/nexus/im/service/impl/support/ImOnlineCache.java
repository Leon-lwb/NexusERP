package com.nexus.im.service.impl.support;

import com.nexus.common.redis.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IM 在线状态缓存
 * <p>基于 Redis 记录用户最后活跃时间（心跳写入），key: im:online:{userId}。
 * <ul>
 *   <li>心跳/登录/消息发送侧调用 {@link #touch} 刷新 TTL（90 秒）</li>
 *   <li>登出侧调用 {@link #offline} 删除标记</li>
 *   <li>查询侧调用 {@link #batchOnlineInfo} 批量判断在线与最后活跃时间</li>
 * </ul>
 * Redis 异常时降级为全部离线，不影响主流程。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ImOnlineCache {

    /** 在线标记 key 前缀。 */
    private static final String KEY_PREFIX = "im:online:";

    /** 在线 TTL：需大于前端心跳间隔（30s）的两倍，容忍一次心跳丢失。 */
    private static final Duration ONLINE_TTL = Duration.ofSeconds(90);

    /** 心跳值（占位，真实活跃时间以 Redis 写入时间为准）。 */
    private static final String ONLINE_VALUE = "1";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 刷新用户在线状态（登录、心跳、发消息时调用）。
     */
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            RedisUtils.setCacheObject(KEY_PREFIX + userId, ONLINE_VALUE, ONLINE_TTL);
        } catch (Exception e) {
            log.warn("IM online touch failed, userId={}", userId, e);
        }
    }

    /**
     * 标记用户离线（登出时调用）。
     */
    public void offline(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            RedisUtils.deleteObject(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("IM online offline failed, userId={}", userId, e);
        }
    }

    /**
     * 批量查询在线信息。
     *
     * @param userIds 用户 ID 集合
     * @return userId → {online: Boolean, lastActiveTime: String}；Redis 异常时返回空 Map（视为全部离线）
     */
    public Map<Long, Map<String, Object>> batchOnlineInfo(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Long userId : ids) {
            try {
                // Redisson TTL：-1 = 永不过期，-2 = key 不存在，>0 = 剩余毫秒
                long ttlMillis = RedisUtils.getTimeToLive(KEY_PREFIX + userId);
                boolean exists = ttlMillis > 0;
                Map<String, Object> info = new HashMap<>();
                info.put("online", exists);
                // 活跃时间 = 当前时间 - 已流逝 TTL
                if (exists) {
                    long elapsedMillis = ONLINE_TTL.toMillis() - ttlMillis;
                    Instant activeAt = Instant.now().minusMillis(elapsedMillis);
                    info.put("lastActiveTime", LocalDateTime.ofInstant(activeAt, ZoneId.systemDefault()).format(TIME_FORMATTER));
                }
                result.put(userId, info);
            } catch (Exception e) {
                log.warn("IM online query failed, userId={}", userId, e);
                result.put(userId, Map.of("online", false));
            }
        }
        return result;
    }
}
