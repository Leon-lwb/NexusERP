package com.nexus.report.version;

import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.ReportCompiler;

import java.util.*;

/** Version lookup has no latest-version fallback for published definitions or queued runs. */
public final class ReportEngineRegistry {
    public record Capability(
            int schemaVersion,
            String engineVersion,
            Set<String> features,
            boolean enabled,
            boolean defaultForDraft) {
        public Capability {
            features = Set.copyOf(features);
        }
    }

    private record Key(int schema, String engine) {}

    private final Map<Key, ReportEngineExecutor> executors;
    private final Map<Integer, String> defaults;
    private final Set<String> disabled;

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

    public static ReportEngineRegistry v1(ReportCompiler compiler) {
        return new ReportEngineRegistry(
                List.of(new V1ReportEngineExecutor(compiler)),
                Map.of(1, ReportCompiler.ENGINE_VERSION),
                Set.of());
    }

    public ReportEngineExecutor require(int schema, String version) {
        var executor = executors.get(new Key(schema, version));
        if (executor == null) throw new ReportFault("VERSION_UNSUPPORTED", "所需报表引擎或格式版本未安装");
        if (disabled.contains(version)) throw new ReportFault("ENGINE_DISABLED", "该报表引擎已停用", 409);
        return executor;
    }

    public ReportEngineExecutor draft(int schema) {
        String version = defaults.get(schema);
        if (version == null) throw new ReportFault("VERSION_UNSUPPORTED", "报表草稿格式未安装");
        return require(schema, version);
    }

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
