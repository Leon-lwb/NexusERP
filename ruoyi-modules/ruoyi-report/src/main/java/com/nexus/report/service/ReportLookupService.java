package com.nexus.report.service;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import java.util.*;

/** Resolves a fixed dataset parameter and rechecks current scopes after the business lookup. */
public final class ReportLookupService {
    private record Key(String code, int version, String parameter) {}

    private record Registered(ReportLookupBinding binding, ReportLookupProvider provider) {}

    private final Map<Key, Registered> installed = new HashMap<>();
    private final ReportDatasetVersionService versions;
    private final ReportDefinitionService definitions;

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
                                != null)
                    throw new IllegalStateException("Invalid or duplicate report lookup");
            }
    }

    public List<ReportLookupBinding> bindings(ReportActor actor, String version) {
        var descriptor = versions.forRuntime(actor, version).descriptor();
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        return descriptor.parameters().stream()
                .map(p -> installed.get(new Key(descriptor.code(), descriptor.version(), p.name())))
                .filter(Objects::nonNull)
                .map(Registered::binding)
                .filter(b -> current.has(b.permission()))
                .toList();
    }

    public ReportLookupProvider.Result query(
            ReportActor actor, String version, String parameter, ReportLookupProvider.Query query) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var descriptor = versions.forRuntime(current, version).descriptor();
        if (descriptor.parameters().stream().noneMatch(p -> p.name().equals(parameter)))
            throw ReportFault.invalid("选择器参数未声明");
        var entry = installed.get(new Key(descriptor.code(), descriptor.version(), parameter));
        if (entry == null) throw new ReportFault("LOOKUP_UNSUPPORTED", "该参数没有安装选择器", 404);
        ReportDefinitionService.require(current, entry.binding().permission());
        // Report permission was checked by the contract reader. Apply each entity's organization
        // policy to its own table.
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
        versions.forRuntime(refreshed, version);
        if (!refreshed.equals(current)) throw new ReportFault("SCOPE_CHANGED", "选择器权限已变化", 409);
        return result;
    }
}
