package com.nexus.report.engine;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportFault;

import java.util.*;

/**
 * 已通过字段与类型检查的执行计划，固定依赖、公式顺序和权限范围。
 *
 * @param dsl 不可变的报表定义
 * @param descriptor 当前契约的descriptor配置或状态
 * @param requiredFields 当前契约的requiredFields配置或状态
 * @param expressions 当前契约的expressions配置或状态
 * @param computedOrder 当前契约的computedOrder配置或状态
 * @param scopes 当前契约的scopes配置或状态
 * @param assetRasters 当前契约的assetRasters配置或状态
 */
public record CompiledReport(
        ReportDsl dsl,
        DatasetDescriptor descriptor,
        Set<String> requiredFields,
        Map<String, SafeExpression> expressions,
        List<String> computedOrder,
        List<ReportScope> scopes,
        Map<String, String> assetRasters) {
    /**
     * 保留第一版执行计划构造方式，不引入打印资源。
     *
     * @param dsl 不可变的报表定义
     * @param descriptor 当前契约的descriptor配置或状态
     * @param requiredFields 当前契约的requiredFields配置或状态
     * @param expressions 当前契约的expressions配置或状态
     * @param computedOrder 当前契约的computedOrder配置或状态
     * @param scopes 当前契约的scopes配置或状态
     */
    public CompiledReport(
            ReportDsl dsl,
            DatasetDescriptor descriptor,
            Set<String> requiredFields,
            Map<String, SafeExpression> expressions,
            List<String> computedOrder,
            List<ReportScope> scopes) {
        this(dsl, descriptor, requiredFields, expressions, computedOrder, scopes, Map.of());
    }

    /**
     * 初始化已编译报表并保存所需依赖或不可变值。
     *
     * @param dsl 报表定义
     * @param descriptor 可信数据集契约
     * @param requiredFields 当前操作的 {@code requiredFields} 输入
     * @param expressions 当前操作的 {@code expressions} 输入
     * @param computedOrder 当前操作的 {@code computedOrder} 输入
     * @param scopes 业务权限范围
     * @param assetRasters 当前契约的assetRasters配置或状态
     */
    public CompiledReport {
        requiredFields = Collections.unmodifiableSet(new LinkedHashSet<>(requiredFields));
        expressions = Collections.unmodifiableMap(new LinkedHashMap<>(expressions));
        computedOrder = List.copyOf(computedOrder);
        scopes = List.copyOf(scopes);
        assetRasters = Map.copyOf(assetRasters == null ? Map.of() : assetRasters);
    }

    /**
     * 完成 {@code aggregateExpressions} 对应的已编译报表处理。
     *
     * @return 当前检查或条件操作是否成功
     */
    public boolean aggregateExpressions() {
        return expressions.values().stream().anyMatch(SafeExpression::aggregate);
    }

    /**
     * 构造当前身份的业务执行范围。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public ReportExecutionContext context(ReportActor actor) {
        if (!actor.has(descriptor.permission())) throw ReportFault.denied();
        List<ReportScope> executionScopes = new ArrayList<>();
        executionScopes.add(actor.scopeFor(descriptor.permission()));
        Set<String> permissions = new TreeSet<>();
        for (String name : requiredFields) {
            String permission = descriptor.field(name).permission();
            if (permission != null && !permission.isBlank()) permissions.add(permission);
        }
        for (String permission : permissions) {
            if (!actor.has(permission)) throw ReportFault.denied();
            executionScopes.add(actor.scopeFor(permission));
        }
        return new ReportExecutionContext(actor, executionScopes);
    }
}
