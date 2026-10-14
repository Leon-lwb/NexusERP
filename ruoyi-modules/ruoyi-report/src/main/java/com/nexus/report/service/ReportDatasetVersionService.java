package com.nexus.report.service;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.mapper.JdbcReportStore;

import java.util.*;

/**
 * Read immutable installed contracts, with the same live resource and field guards as execution.
 */
public final class ReportDatasetVersionService {
    public record View(
            String id,
            String datasetId,
            String code,
            int number,
            boolean current,
            DatasetDescriptor descriptor) {}

    public record Change(String category, String name, String kind, boolean breaking) {}

    public record Diff(String from, String to, boolean breaking, List<Change> changes) {
        public Diff {
            changes = List.copyOf(changes);
        }
    }

    private final JdbcReportStore store;
    private final ReportProviderRegistry registry;
    private final ReportDefinitionService definitions;
    private final ReportCompiler compiler;

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

    public View read(ReportActor actor, String id) {
        return read(actor, id, false);
    }

    public View forRuntime(ReportActor actor, String id) {
        return read(actor, id, true);
    }

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
        var descriptor = registry.get(version.provider(), version.providerVersion()).descriptor();
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
        // Both reads are repeated after comparison so a revocation cannot leak an earlier
        // descriptor.
        if (!left.descriptor().equals(read(actor, from).descriptor())
                || !right.descriptor().equals(read(actor, to).descriptor()))
            throw new ReportFault("SCOPE_CHANGED", "数据契约可见范围已变化", 409);
        return new Diff(from, to, changes.stream().anyMatch(Change::breaking), changes);
    }

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
