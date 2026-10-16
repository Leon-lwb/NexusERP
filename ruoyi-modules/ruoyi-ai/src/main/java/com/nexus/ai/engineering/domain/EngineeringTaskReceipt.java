package com.nexus.ai.engineering.domain;

/**
 * 不含租约凭证的事件回执，同序号同内容重试必须返回相同值。
 *
 * @param taskId 服务端任务编号，对外使用十进制字符串
 * @param attempt 本次领取尝试编号，旧尝试禁止写入
 * @param revision 服务端修订号，与步骤序号分开
 * @param state 服务端实际任务状态，不能自报任意终态
 * @param stage 协议定义的固定执行阶段
 * @param cancelRequested 服务端已请求在步骤边界停止
 */
public record EngineeringTaskReceipt(
        String taskId,
        int attempt,
        long revision,
        String state,
        String stage,
        boolean cancelRequested) {}
