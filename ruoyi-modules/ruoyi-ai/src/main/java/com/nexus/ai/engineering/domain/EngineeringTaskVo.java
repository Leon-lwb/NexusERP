package com.nexus.ai.engineering.domain;

import java.util.List;
import java.util.Map;

/**
 * 可恢复的工程任务安全视图，编号始终为十进制字符串，不含凭证和对象键。
 *
 * @param taskId 服务端任务编号，对外使用十进制字符串
 * @param deviceId 当前用户显式绑定的设备编号
 * @param sessionId 已校验属于当前用户的会话编号
 * @param requestId 服务端捕获的稳定聊天请求标识
 * @param parentTaskId 同一 owner 的原任务编号，不接受本地路径
 * @param action 固定受控工程动作
 * @param state 服务端实际任务状态，不能自报任意终态
 * @param stage 协议定义的固定执行阶段
 * @param attempt 本次领取尝试编号，旧尝试禁止写入
 * @param revision 服务端修订号，与步骤序号分开
 * @param cancelRequested 服务端已请求在步骤边界停止
 * @param errorCode 稳定协议错误类别，不包含原始异常信息
 * @param errorMessage 不含敏感数据的中文错误摘要
 * @param createdAt UTC 创建时间
 * @param updatedAt UTC 最近更新时间
 * @param previewPath 本人任务详情的前端路由
 * @param events 有界实际阶段记录
 * @param protocolVersion 固定协议主版本 nexus-engineering/1
 * @param templateId 允许目录中的模板标识
 * @param templateVersion 已审模板固定版本
 * @param templateSha256 已审模板归档完整 SHA-256
 * @param parameters 仅允许目录声明的有界结构化板参数
 * @param leaseExpiresAt UTC ISO-8601 租约到期时间
 * @param deadlineAt UTC ISO-8601 运行总截止时间
 * @param connectionDiagnostic 已保存的连接检查摘要，历史任务可以为空
 */
public record EngineeringTaskVo(
        String taskId,
        String deviceId,
        String sessionId,
        String requestId,
        String parentTaskId,
        String action,
        String state,
        String stage,
        int attempt,
        long revision,
        boolean cancelRequested,
        String errorCode,
        String errorMessage,
        String createdAt,
        String updatedAt,
        String previewPath,
        List<?> events,
        String protocolVersion,
        String templateId,
        String templateVersion,
        String templateSha256,
        Map<String, Object> parameters,
        String leaseExpiresAt,
        String deadlineAt,
        EngineeringConnectionDiagnostic connectionDiagnostic) {}
