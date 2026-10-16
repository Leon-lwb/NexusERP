package com.nexus.common.ai.engineering;

import java.util.Map;

/**
 * 工程任务创建参数；板尺寸用十进制字符串，禁止接受任意路径、代码及 URL。
 *
 * @param protocolVersion 固定协议主版本 nexus-engineering/1
 * @param deviceId 当前用户显式绑定的设备编号
 * @param action 固定受控工程动作
 * @param idempotencyKey 当前 owner 作用域内的稳定幂等键
 * @param sessionId 已校验属于当前用户的会话编号
 * @param templateId 允许目录中的模板标识
 * @param templateVersion 已审模板固定版本
 * @param templateSha256 已审模板归档完整 SHA-256
 * @param parentTaskId 同一 owner 的原任务编号，不接受本地路径
 * @param parameters 仅允许目录声明的有界结构化板参数
 */
public record EngineeringTaskCommand(
        String protocolVersion,
        Long deviceId,
        EngineeringAction action,
        String idempotencyKey,
        Long sessionId,
        String templateId,
        String templateVersion,
        String templateSha256,
        Long parentTaskId,
        Map<String, Object> parameters) {}
