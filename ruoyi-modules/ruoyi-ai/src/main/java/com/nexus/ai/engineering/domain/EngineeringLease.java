package com.nexus.ai.engineering.domain;

/**
 * 当前领取回调参数；租约令牌不得写入日志、事件 JSON 或实体。
 *
 * @param attempt 本次领取尝试编号，旧尝试禁止写入
 * @param leaseToken 随机租约原文，禁止存储到日志或普通任务视图
 * @param expectedRevision 调用者期望的当前修订号，用于 CAS
 */
public record EngineeringLease(int attempt, String leaseToken, long expectedRevision) {
    @Override
    public String toString() {
        return "EngineeringLease[租约已隐藏]";
    }
}
