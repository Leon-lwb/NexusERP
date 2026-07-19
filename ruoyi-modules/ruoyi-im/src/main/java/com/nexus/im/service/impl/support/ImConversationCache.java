package com.nexus.im.service.impl.support;

import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.vo.ImConversationVo;
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
 *
 * @author nexus
 */
@Slf4j
@Component
public class ImConversationCache {

    /** 缓存 TTL：60 秒，平衡新鲜度与性能 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /**
     * 读取当前用户的会话列表缓存。
     *
     * @return 命中则返回缓存列表，未命中或 Redis 不可用时返回 null
     */
    @SuppressWarnings("unchecked")
    public List<ImConversationVo> get() {
        try {
            return RedisUtils.getCacheObject(buildKey());
        } catch (Exception e) {
            log.debug("[IM] 会话缓存读取失败，降级直查 DB: {}", e.getMessage());
            return null;
        }
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
