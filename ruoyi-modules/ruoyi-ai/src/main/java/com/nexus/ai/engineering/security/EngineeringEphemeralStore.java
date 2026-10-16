package com.nexus.ai.engineering.security;

import java.time.Duration;

/** 短期单次凭证仓储；生产使用 Redis 原子消费，不在数据库保存原文。 */
public interface EngineeringEphemeralStore {
    /** 按用途及凭证摘要保存有界负载。 */
    void put(String purpose, String token, String body, Duration ttl);

    /** 原子读取并删除；过期或已使用返回空值。 */
    String consume(String purpose, String token);
}
