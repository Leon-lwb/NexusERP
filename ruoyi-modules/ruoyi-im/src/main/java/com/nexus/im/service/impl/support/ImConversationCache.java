package com.nexus.im.service.impl.support;

import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.push.ImMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * P4-2: IM 会话列表 Redis 缓存。
 * <p>缓存 key: im:conv:{tenantId}:{userId}，TTL 60s。
 * <p>缓存值: List<ImConversationVo> 的 JSON 序列化（Redisson 自动通过 Kryo/JSON 编解码）。
 * <p>失效场景：发送消息 / 标记已读 / 群组变更时主动 evict。
 * <p>容错：Redis 不可用时降级为直查 DB（catch 异常返回 null）。
 * <p>P5-2: 在 {@link #get()} 入口埋点 im.cache.hit / im.cache.miss 计数器（tag: type=conversation），
 * 供 Prometheus 观测命中率。Redis 异常也计为 miss（从应用视角等同于缓存未生效）。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImConversationCache {

    /** 缓存 TTL：60 秒，平衡新鲜度与性能 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** P5-2: 缓存类型 tag（区分会话缓存与其他可能的缓存） */
    private static final String CACHE_TYPE = "conversation";

    private final ImMetrics imMetrics;

    /**
     * 读取当前用户的会话列表缓存。
     * <p>P5-2: 命中调用 {@code imMetrics.recordCacheHit}，未命中/异常调用 {@code recordCacheMiss}。
     *
     * @return 命中则返回缓存列表，未命中或 Redis 不可用时返回 null
     */
    public List<ImConversationVo> get() {
        try {
            List<ImConversationVo> cached = readFromRedis(buildKey());
            if (cached != null) {
                imMetrics.recordCacheHit(CACHE_TYPE);
                return cached;
            }
            imMetrics.recordCacheMiss(CACHE_TYPE);
            return null;
        } catch (Exception e) {
            // P5-2: Redis 异常视为 miss（缓存对应用层无效，仍需走 DB）
            imMetrics.recordCacheMiss(CACHE_TYPE);
            log.debug("[IM] 会话缓存读取失败，降级直查 DB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * P5-2: 从 Redis 读取缓存值（抽取为 protected 便于单元测试覆盖）。
     * <p>测试子类可重写此方法，避免触发 {@code RedisUtils} 静态初始化（依赖 SpringUtils.getBean）。
     */
    @SuppressWarnings("unchecked")
    protected List<ImConversationVo> readFromRedis(String key) {
        return RedisUtils.getCacheObject(key);
    }

    /**
     * 写入当前用户的会话列表缓存。
     */
    public void put(List<ImConversationVo> conversations) {
        if (conversations == null) {
            return;
        }
        try {
            RedisUtils.setCacheObject(buildKey(), conversations, CACHE_TTL);
        } catch (Exception e) {
            log.debug("[IM] 会话缓存写入失败，忽略: {}", e.getMessage());
        }
    }

    /**
     * 失效当前用户的会话列表缓存。
     */
    public void evict() {
        try {
            RedisUtils.deleteObject(buildKey());
        } catch (Exception e) {
            log.debug("[IM] 会话缓存失效失败，等待 TTL 过期: {}", e.getMessage());
        }
    }

    /**
     * 失效指定用户的会话列表缓存（群消息场景：群内其他成员的缓存也需失效）。
     */
    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            RedisUtils.deleteObject(buildKeyForUser(userId));
        } catch (Exception e) {
            log.debug("[IM] 会话缓存失效失败(userId={}): {}", userId, e.getMessage());
        }
    }

    /**
     * 批量失效多个用户的会话缓存（群消息扇出场景）。
     */
    public void evictUsers(Iterable<Long> userIds) {
        if (userIds == null) {
            return;
        }
        for (Long uid : userIds) {
            evictUser(uid);
        }
    }

    private String buildKey() {
        return buildKeyForUser(LoginHelper.getUserId());
    }

    private String buildKeyForUser(Long userId) {
        String tenantId = TenantHelper.getTenantId();
        return "im:conv:" + (tenantId == null ? "default" : tenantId) + ":" + userId;
    }
}
