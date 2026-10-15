package com.nexus.report.version;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.ReportCompiler;

import java.util.*;

/** 发布定义和已排队运行按精确版本查找，不回退到最新执行器。 */
public final class ReportEngineRegistry {
    /**
     * Capability，负责当前模块对应的契约或处理职责。
     *
     * @param schemaVersion 当前契约的schemaVersion配置或状态
     * @param engineVersion 当前契约的engineVersion配置或状态
     * @param features 当前契约的features配置或状态
     * @param enabled 当前契约的enabled配置或状态
     * @param defaultForDraft 当前契约的defaultForDraft配置或状态
     */
    public record Capability(
            int schemaVersion,
            String engineVersion,
            Set<String> features,
            boolean enabled,
            boolean defaultForDraft) {
        /**
         * 初始化Capability并保存所需依赖或不可变值。
         *
         * @param schemaVersion 当前操作的 {@code schemaVersion} 输入
         * @param engineVersion 当前操作的 {@code engineVersion} 输入
         * @param features 当前操作的 {@code features} 输入
         * @param enabled 当前操作的 {@code enabled} 输入
         * @param defaultForDraft 当前操作的 {@code defaultForDraft} 输入
         */
        public Capability {
            features = Set.copyOf(features);
        }
    }

    /**
     * Key，负责当前模块对应的契约或处理职责。
     *
     * @param schema 当前契约的schema配置或状态
     * @param engine 当前契约的engine配置或状态
     */
    private record Key(int schema, String engine) {}

    /** 保存当前 {@code executors} 值。 */
    private final Map<Key, ReportEngineExecutor> executors;

    /** 保存当前 {@code defaults} 值。 */
    private final Map<Integer, String> defaults;

    /** 保存当前 {@code disabled} 值。 */
    private final Set<String> disabled;

    /**
     * 初始化报表引擎注册表并保存所需依赖或不可变值。
     *
     * @param installed 当前操作的 {@code installed} 输入
     * @param defaults 当前操作的 {@code defaults} 输入
     * @param disabled 当前操作的 {@code disabled} 输入
     */
    public ReportEngineRegistry(
            Collection<ReportEngineExecutor> installed,
            Map<Integer, String> defaults,
            Set<String> disabled) {
        var entries = new HashMap<Key, ReportEngineExecutor>();
        for (var executor : installed) {
            if (executor.schemaVersion() < 1
                    || executor.engineVersion() == null
                    || !executor.engineVersion().matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
                throw new IllegalStateException("Invalid report executor identity");
            if (entries.putIfAbsent(
                            new Key(executor.schemaVersion(), executor.engineVersion()), executor)
                    != null) throw new IllegalStateException("Duplicate report executor identity");
        }
        this.executors = Map.copyOf(entries);
        this.defaults = Map.copyOf(defaults);
        this.disabled = Set.copyOf(disabled);
        defaults.forEach(
                (schema, version) -> {
                    if (!entries.containsKey(new Key(schema, version)))
                        throw new IllegalStateException("Draft executor is not installed");
                });
    }

    /**
     * 完成 {@code v1} 对应的报表引擎注册表处理。
     *
     * @param compiler 报表编译器
     * @return 当前处理返回的固定结果
     */
    public static ReportEngineRegistry v1(ReportCompiler compiler) {
        return new ReportEngineRegistry(
                List.of(new V1ReportEngineExecutor(compiler)),
                Map.of(1, ReportCompiler.ENGINE_VERSION),
                Set.of());
    }

    /**
     * 检查当前操作所需权限或版本。
     *
     * @param schema 定义格式版本
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    public ReportEngineExecutor require(int schema, String version) {
        var executor = executors.get(new Key(schema, version));
        if (executor == null) throw new ReportFault("VERSION_UNSUPPORTED", "所需报表引擎或格式版本未安装");
        if (disabled.contains(version)) throw new ReportFault("ENGINE_DISABLED", "该报表引擎已停用", 409);
        return executor;
    }

    /**
     * 完成 {@code draft} 对应的报表引擎注册表处理。
     *
     * @param schema 定义格式版本
     * @return 当前处理返回的固定结果
     */
    public ReportEngineExecutor draft(int schema) {
        String version = defaults.get(schema);
        if (version == null) throw new ReportFault("VERSION_UNSUPPORTED", "报表草稿格式未安装");
        return require(schema, version);
    }

    /**
     * 列出已安装执行器的格式与能力。
     *
     * @return 当前处理返回的固定结果
     */
    public List<Capability> capabilities() {
        return executors.entrySet().stream()
                .sorted(
                        Comparator.comparingInt(
                                        (Map.Entry<Key, ReportEngineExecutor> e) ->
                                                e.getKey().schema())
                                .thenComparing(e -> e.getKey().engine()))
                .map(
                        e ->
                                new Capability(
                                        e.getKey().schema(),
                                        e.getKey().engine(),
                                        e.getValue().capabilities(),
                                        !disabled.contains(e.getKey().engine()),
                                        Objects.equals(
                                                defaults.get(e.getKey().schema()),
                                                e.getKey().engine())))
                .toList();
    }
}
