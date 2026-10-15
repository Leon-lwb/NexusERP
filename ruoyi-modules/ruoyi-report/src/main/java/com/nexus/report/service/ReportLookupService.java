package com.nexus.report.service;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import java.util.*;

/** 解析固定数据集参数，业务候选查询完成后再次检查当前权限范围。 */
public final class ReportLookupService {
    /**
     * Key，负责当前模块对应的契约或处理职责。
     *
     * @param code 当前契约的code配置或状态
     * @param version 当前契约的version配置或状态
     * @param parameter 当前契约的parameter配置或状态
     */
    private record Key(String code, int version, String parameter) {}

    /**
     * Registered，负责当前模块对应的契约或处理职责。
     *
     * @param binding 当前契约的binding配置或状态
     * @param provider 当前契约的provider配置或状态
     */
    private record Registered(ReportLookupBinding binding, ReportLookupProvider provider) {}

    /** 保存当前 {@code installed} 值。 */
    private final Map<Key, Registered> installed = new HashMap<>();

    /** 保存当前 {@code versions} 值。 */
    private final ReportDatasetVersionService versions;

    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /**
     * 初始化报表选择器服务并保存所需依赖或不可变值。
     *
     * @param versions 当前操作的 {@code versions} 输入
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param providers 当前操作的 {@code providers} 输入
     */
    public ReportLookupService(
            ReportDatasetVersionService versions,
            ReportDefinitionService definitions,
            List<ReportLookupProvider> providers) {
        this.versions = versions;
        this.definitions = definitions;
        for (var provider : providers)
            for (var binding : provider.bindings()) {
                if (binding.permission() == null
                        || binding.permission().isBlank()
                        || binding.providerVersion() < 1
                        || installed.putIfAbsent(
                                        new Key(
                                                binding.datasetCode(),
                                                binding.providerVersion(),
                                                binding.parameter()),
                                        new Registered(binding, provider))
                                != null) throw new IllegalStateException("报表选择器绑定无效或重复");
            }
    }

    /**
     * 列出固定契约已安装且有权限的选择器。
     *
     * @param actor 当前服务端身份
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    public List<ReportLookupBinding> bindings(ReportActor actor, String version) {
        var descriptor = versions.forLookup(actor, version).descriptor();
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        return descriptor.parameters().stream()
                .map(p -> installed.get(new Key(descriptor.code(), descriptor.version(), p.name())))
                .filter(Objects::nonNull)
                .map(Registered::binding)
                .filter(b -> current.has(b.permission()))
                .toList();
    }

    /**
     * 执行受控只读查询。
     *
     * @param actor 当前服务端身份
     * @param version 固定版本
     * @param parameter 当前操作的 {@code parameter} 输入
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    public ReportLookupProvider.Result query(
            ReportActor actor, String version, String parameter, ReportLookupProvider.Query query) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var descriptor = versions.forLookup(current, version).descriptor();
        if (descriptor.parameters().stream().noneMatch(p -> p.name().equals(parameter)))
            throw ReportFault.invalid("选择器参数未声明");
        var entry = installed.get(new Key(descriptor.code(), descriptor.version(), parameter));
        if (entry == null) throw new ReportFault("LOOKUP_UNSUPPORTED", "该参数没有安装选择器", 404);
        ReportDefinitionService.require(current, entry.binding().permission());
        /** 契约读取阶段已校验报表权限；各实体表分别应用其自身业务组织范围。 */
        /** 每张业务表使用对应实体的访问策略。 */
        var scopes = List.of(current.scopeFor(entry.binding().permission()));
        var result =
                entry.provider()
                        .query(entry.binding(), new ReportExecutionContext(current, scopes), query);
        if (result.options().size() > query.size()
                || result.total() < 0
                || result.options().stream()
                        .anyMatch(
                                o ->
                                        o.value() == null
                                                || o.label() == null
                                                || o.label().length() > 500))
            throw ReportFault.capacity();
        var refreshed = definitions.resolve(current.tenantId(), current.userId());
        versions.forLookup(refreshed, version);
        if (!refreshed.equals(current)) throw new ReportFault("SCOPE_CHANGED", "选择器权限已变化", 409);
        return result;
    }

    /**
     * 按完整ID或编码读取名称，单独校验当前参数，不要求填写其余报表参数。
     *
     * @param actor 当前服务端身份
     * @param version 固定数据集版本ID
     * @param parameter 已声明的业务参数
     * @param value 未经浮点转换的完整值
     * @return 当前可见对象；不存在或不可见时为空
     */
    public Optional<ReportLookupProvider.Option> selected(
            ReportActor actor, String version, String parameter, String value) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var descriptor = versions.forLookup(current, version).descriptor();
        var declared =
                descriptor.parameters().stream()
                        .filter(p -> p.name().equals(parameter))
                        .findFirst()
                        .orElseThrow(() -> ReportFault.invalid("选择器参数未声明"));
        var entry = installed.get(new Key(descriptor.code(), descriptor.version(), parameter));
        if (entry == null) throw new ReportFault("LOOKUP_UNSUPPORTED", "该参数没有安装选择器", 404);
        ReportDefinitionService.require(current, entry.binding().permission());
        if (value == null || value.isBlank() || value.length() > 500)
            throw ReportFault.invalid("候选值无效");
        var single =
                new DatasetDescriptor(
                        descriptor.code(),
                        descriptor.name(),
                        descriptor.version(),
                        descriptor.permission(),
                        descriptor.document(),
                        descriptor.fields(),
                        List.of(declared),
                        descriptor.defaultColumns());
        String normalized =
                DatasetQueryValidator.parameters(single, Map.of(parameter, value))
                        .get(parameter)
                        .toString();
        var scopes = List.of(current.scopeFor(entry.binding().permission()));
        var result =
                entry.provider()
                        .selected(
                                entry.binding(),
                                new ReportExecutionContext(current, scopes),
                                normalized);
        if (result == null
                || result.isPresent()
                        && (!normalized.equals(result.get().value())
                                || result.get().label() == null
                                || result.get().label().length() > 500))
            throw ReportFault.capacity();
        var refreshed = definitions.resolve(current.tenantId(), current.userId());
        versions.forLookup(refreshed, version);
        if (!refreshed.equals(current)) throw new ReportFault("SCOPE_CHANGED", "选择器权限已变化", 409);
        return result;
    }
}
