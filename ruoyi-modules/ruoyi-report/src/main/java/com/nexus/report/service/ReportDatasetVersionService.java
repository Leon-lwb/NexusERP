package com.nexus.report.service;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.mapper.JdbcReportStore;

import java.util.*;

/** 读取不可变且已安装的契约，并应用与执行相同的当前资源和字段权限。 */
public final class ReportDatasetVersionService {
    /**
     * View，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param datasetId 当前契约的datasetId配置或状态
     * @param code 当前契约的code配置或状态
     * @param number 当前契约的number配置或状态
     * @param current 当前契约的current配置或状态
     * @param descriptor 当前契约的descriptor配置或状态
     */
    public record View(
            String id,
            String datasetId,
            String code,
            int number,
            boolean current,
            DatasetDescriptor descriptor) {}

    /**
     * Change，负责当前模块对应的契约或处理职责。
     *
     * @param category 当前契约的category配置或状态
     * @param name 契约名称
     * @param kind 当前契约的kind配置或状态
     * @param breaking 当前契约的breaking配置或状态
     */
    public record Change(String category, String name, String kind, boolean breaking) {}

    /**
     * Diff，负责当前模块对应的契约或处理职责。
     *
     * @param from 当前契约的from配置或状态
     * @param to 当前契约的to配置或状态
     * @param breaking 当前契约的breaking配置或状态
     * @param changes 当前契约的changes配置或状态
     */
    public record Diff(String from, String to, boolean breaking, List<Change> changes) {

        /**
         * 初始化Diff并保存所需依赖或不可变值。
         *
         * @param from 当前操作的 {@code from} 输入
         * @param to 当前操作的 {@code to} 输入
         * @param breaking 当前操作的 {@code breaking} 输入
         * @param changes 当前操作的 {@code changes} 输入
         */
        public Diff {
            changes = List.copyOf(changes);
        }
    }

    /** 租户化元数据或私有存储。 */
    private final JdbcReportStore store;

    /** 代码契约注册表。 */
    private final ReportProviderRegistry registry;

    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /** 报表编译器。 */
    private final ReportCompiler compiler;

    /**
     * 初始化报表数据集版本服务并保存所需依赖或不可变值。
     *
     * @param store 租户化元数据或私有存储
     * @param registry 代码契约注册表
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     */
    public ReportDatasetVersionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportDefinitionService definitions,
            ReportCompiler compiler) {
        this.store = store;
        this.registry = registry;
        this.definitions = definitions;
        this.compiler = compiler;
    }

    /**
     * 读取并检查当前租户私有产物。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public View read(ReportActor actor, String id) {
        return read(actor, id, false);
    }

    /**
     * 完成 {@code forRuntime} 对应的报表数据集版本服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public View forRuntime(ReportActor actor, String id) {
        return read(actor, id, true);
    }

    /**
     * 读取并检查当前租户私有产物。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param runtime 当前操作的 {@code runtime} 输入
     * @return 当前处理返回的固定结果
     */
    private View read(ReportActor actor, String id, boolean runtime) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(
                current, runtime ? "report:runtime:preview" : "report:dataset:list");
        var version =
                store.datasetVersion(current.tenantId(), id)
                        .orElseThrow(() -> new ReportFault("RESOURCE_NOT_FOUND", "数据集版本不存在", 404));
        var dataset =
                store.dataset(current.tenantId(), version.datasetId())
                        .orElseThrow(ReportFault::denied);
        if (!dataset.status().equals("ACTIVE")
                || !store.allowed(current, "DATASET", dataset.id(), dataset.owner(), "VIEW"))
            throw ReportFault.denied();
        var descriptor = definitions.resolveDataset(current, version).descriptor();
        if (!dataset.code().equals(descriptor.code())
                || !ReportDefinitionService.hash(version.json()).equals(version.checksum())
                || !ReportDefinitionService.hash(compiler.json(descriptor))
                        .equals(version.checksum()))
            throw new ReportFault("VERSION_INVALID", "数据集契约完整性校验失败", 409);
        ReportDefinitionService.require(current, descriptor.permission());
        var fields =
                descriptor.fields().stream()
                        .filter(
                                f ->
                                        f.permission() == null
                                                || f.permission().isBlank()
                                                || current.has(f.permission()))
                        .toList();
        var names = new HashSet<String>();
        fields.forEach(f -> names.add(f.name()));
        var visible =
                new DatasetDescriptor(
                        descriptor.code(),
                        descriptor.name(),
                        descriptor.version(),
                        descriptor.permission(),
                        descriptor.document(),
                        fields,
                        descriptor.parameters(),
                        descriptor.defaultColumns().stream().filter(names::contains).toList());
        var refreshed = definitions.resolve(current.tenantId(), current.userId());
        if (!refreshed.equals(current)
                || !store.allowed(refreshed, "DATASET", dataset.id(), dataset.owner(), "VIEW")
                || !store.dataset(current.tenantId(), dataset.id())
                        .orElseThrow(ReportFault::denied)
                        .status()
                        .equals("ACTIVE")) throw ReportFault.denied();
        return new View(
                version.id(),
                dataset.id(),
                dataset.code(),
                version.number(),
                Objects.equals(dataset.publishedVersion(), version.id()),
                visible);
    }

    /**
     * 比较两份已授权历史契约并复核撤权。
     *
     * @param actor 当前服务端身份
     * @param from 当前操作的 {@code from} 输入
     * @param to 当前操作的 {@code to} 输入
     * @return 当前处理返回的固定结果
     */
    public Diff diff(ReportActor actor, String from, String to) {
        var left = read(actor, from);
        var right = read(actor, to);
        if (!left.datasetId().equals(right.datasetId())) throw ReportFault.invalid("只能比较同一数据集的版本");
        var changes = new ArrayList<Change>();
        compare(
                left.descriptor().fields(),
                right.descriptor().fields(),
                DatasetField::name,
                "FIELD",
                changes);
        compare(
                left.descriptor().parameters(),
                right.descriptor().parameters(),
                DatasetParameter::name,
                "PARAMETER",
                changes);
        /** 比较后重新读取两份契约，防止撤权后泄露较早读取的描述。 */
        /** 数据集契约描述。 */
        if (!left.descriptor().equals(read(actor, from).descriptor())
                || !right.descriptor().equals(read(actor, to).descriptor()))
            throw new ReportFault("SCOPE_CHANGED", "数据契约可见范围已变化", 409);
        return new Diff(from, to, changes.stream().anyMatch(Change::breaking), changes);
    }

    /**
     * 完成 {@code compare} 对应的报表数据集版本服务处理。
     *
     * @param old 当前操作的 {@code old} 输入
     * @param next 当前操作的 {@code next} 输入
     * @param name 当前操作的 {@code name} 输入
     * @param category 当前操作的 {@code category} 输入
     * @param changes 当前操作的 {@code changes} 输入
     */
    private static <T> void compare(
            List<T> old,
            List<T> next,
            java.util.function.Function<T, String> name,
            String category,
            List<Change> changes) {
        var prior = new LinkedHashMap<String, T>();
        var after = new LinkedHashMap<String, T>();
        old.forEach(x -> prior.put(name.apply(x), x));
        next.forEach(x -> after.put(name.apply(x), x));
        for (var entry : prior.entrySet()) {
            var replacement = after.get(entry.getKey());
            if (replacement == null)
                changes.add(new Change(category, entry.getKey(), "REMOVED", true));
            else if (!entry.getValue().equals(replacement))
                changes.add(new Change(category, entry.getKey(), "CHANGED", true));
        }
        for (var entry : after.entrySet())
            if (!prior.containsKey(entry.getKey()))
                changes.add(
                        new Change(
                                category,
                                entry.getKey(),
                                "ADDED",
                                entry.getValue() instanceof DatasetParameter p
                                        && p.required()
                                        && p.defaultValue() == null));
    }
}
