package com.nexus.ai.engineering.security;

import com.nexus.common.core.constant.GlobalConstants;

import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 配对码和上传票据的 Redis 摘要存储，getAndDelete 保证跨实例单次消费。 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringRedisEphemeralStore implements EngineeringEphemeralStore {
    private final RedissonClient redis;

    /** 注入现有 Redis 客户端，不新建服务连接。 */
    public EngineeringRedisEphemeralStore(RedissonClient redis) {
        this.redis = redis;
    }

    /** 保存摘要命名空间和过期时间，负载不含凭证原文。 */
    public void put(String purpose, String token, String body, Duration ttl) {
        redis.getBucket(key(purpose, token), StringCodec.INSTANCE).set(body, ttl);
    }

    /** 原子消费短期票据。 */
    public String consume(String purpose, String token) {
        return (String) redis.getBucket(key(purpose, token), StringCodec.INSTANCE).getAndDelete();
    }

    private String key(String purpose, String token) {
        // 配对从已登录浏览器流向匿名连接器，两端必须使用相同的全局摘要键。
        // 所有者由配对负载固定并在兑换时复核；已认证上传票据仍使用租户键。
        String prefix = "pair".equals(purpose) ? GlobalConstants.GLOBAL_REDIS_KEY : "";
        return prefix + "ai:engineering:" + purpose + ":" + EngineeringSecrets.hash(token);
    }
}
