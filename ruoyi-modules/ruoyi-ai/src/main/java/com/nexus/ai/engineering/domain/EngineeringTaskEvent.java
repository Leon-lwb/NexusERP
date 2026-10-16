package com.nexus.ai.engineering.domain;

import com.nexus.common.ai.engineering.EngineeringStage;

/**
 * 固定阶段事件，步骤序号用于幂等，修订号用于并发控制。
 *
 * @param attempt 本次领取尝试编号，旧尝试禁止写入
 * @param leaseToken 随机租约原文，禁止存储到日志或普通任务视图
 * @param expectedRevision 调用者期望的当前修订号，用于 CAS
 * @param sequence 连续递增的步骤序号，最多六十四项
 * @param stage 协议定义的固定执行阶段
 * @param message 供用户阅读的有界摘要
 * @param cancelAcknowledged 连接器已在步骤边界停止的确认
 * @param errorCode 稳定协议错误类别，不包含原始异常信息
 */
public record EngineeringTaskEvent(
        int attempt,
        String leaseToken,
        long expectedRevision,
        int sequence,
        EngineeringStage stage,
        String message,
        boolean cancelAcknowledged,
        String errorCode) {
    /** 提取租约校验字段，不接受客户端任务终态。 */
    public EngineeringLease lease() {
        return new EngineeringLease(attempt, leaseToken, expectedRevision);
    }

    @Override
    public String toString() {
        return "EngineeringTaskEvent[sequence=" + sequence + ",stage=" + stage + "]";
    }
}
