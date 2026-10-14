package com.nexus.common.report;

import java.util.*;

/**
 * 仅在服务端捕获执行身份，不在线程或任务间传播登录令牌和请求上下文。
 *
 * @param actor 执行用户
 * @param scopes 当前契约的scopes配置或状态
 * @param cancellation 当前契约的cancellation配置或状态
 */
public record ReportExecutionContext(
        ReportActor actor, List<ReportScope> scopes, ReportCancellation cancellation) {

    /**
     * 初始化报表执行上下文并保存所需依赖或不可变值。
     *
     * @param actor 当前服务端身份
     * @param scopes 业务权限范围
     * @param cancellation 执行取消信号
     */
    public ReportExecutionContext {
        Objects.requireNonNull(actor);
        scopes = List.copyOf(scopes);
        Objects.requireNonNull(cancellation);
        if (scopes.isEmpty()) throw new IllegalArgumentException("Query scope required");
    }

    /**
     * 初始化报表执行上下文并保存所需依赖或不可变值。
     *
     * @param actor 当前服务端身份
     * @param scopes 业务权限范围
     */
    public ReportExecutionContext(ReportActor actor, List<ReportScope> scopes) {
        this(actor, scopes, new ReportCancellation());
    }

    /**
     * 完成 {@code tenantId} 对应的报表执行上下文处理。
     *
     * @return 当前处理返回的固定结果
     */
    public String tenantId() {
        return actor.tenantId();
    }
}
