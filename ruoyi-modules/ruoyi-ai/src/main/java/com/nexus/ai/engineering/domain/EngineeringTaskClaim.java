package com.nexus.ai.engineering.domain;

import java.util.Map;

/**
 * 仅向已认证执行设备返回的领取数据，板参数不包含目录、代码及任意 URL。
 *
 * @param protocolVersion 固定协议主版本 nexus-engineering/1
 * @param taskId 服务端任务编号，对外使用十进制字符串
 * @param action 固定受控工程动作
 * @param attempt 本次领取尝试编号，旧尝试禁止写入
 * @param revision 服务端修订号，与步骤序号分开
 * @param leaseToken 随机租约原文，禁止存储到日志或普通任务视图
 * @param leaseExpiresAt UTC ISO-8601 租约到期时间
 * @param deadlineAt UTC ISO-8601 运行总截止时间
 * @param templateId 允许目录中的模板标识
 * @param templateVersion 已审模板固定版本
 * @param templateSha256 已审模板归档完整 SHA-256
 * @param parentTaskId 同一 owner 的原任务编号，不接受本地路径
 * @param parameters 仅允许目录声明的有界结构化板参数
 * @param cancelRequested 服务端已请求在步骤边界停止
 */
public record EngineeringTaskClaim(
        String protocolVersion,
        String taskId,
        String action,
        int attempt,
        long revision,
        String leaseToken,
        String leaseExpiresAt,
        String deadlineAt,
        String templateId,
        String templateVersion,
        String templateSha256,
        String parentTaskId,
        Map<String, Object> parameters,
        boolean cancelRequested) {
    @Override
    public String toString() {
        return "EngineeringTaskClaim[taskId=" + taskId + ",attempt=" + attempt + "]";
    }
}
