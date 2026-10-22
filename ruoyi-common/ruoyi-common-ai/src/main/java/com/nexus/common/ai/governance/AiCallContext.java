package com.nexus.common.ai.governance;

/** 单次调用的可信业务身份；不能从客户端直接绑定。 */
public record AiCallContext(
        String tenantId,
        Long userId,
        String userName,
        String requestId,
        Long sessionId,
        Long flowInstanceId,
        Long decisionRunId,
        String abRunId,
        String source) {
    public AiCallContext {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9_-]{1,20}") || userId == null)
            throw new IllegalArgumentException("模型调用缺少可信租户或用户身份");
        if (source == null || source.length() > 32) throw new IllegalArgumentException("模型调用来源不合法");
    }
}
