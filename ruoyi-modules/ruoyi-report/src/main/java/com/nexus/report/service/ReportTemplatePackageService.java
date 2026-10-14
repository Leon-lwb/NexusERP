package com.nexus.report.service;

import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.Definition;
import com.nexus.report.engine.ReportCompiler;

import java.util.*;

/** Portable definitions contain no tenant grants, business defaults or runtime artifacts. */
public final class ReportTemplatePackageService {
    public record Dependency(String code, int providerVersion, String checksum) {}

    public record TemplatePackage(
            int formatVersion,
            String engine,
            ReportDsl dsl,
            Dependency dependency,
            String checksum) {}

    private record Content(
            int formatVersion, String engine, ReportDsl dsl, Dependency dependency) {}

    private final ReportDefinitionService definitions;
    private final ReportCompiler compiler;

    public ReportTemplatePackageService(
            ReportDefinitionService definitions, ReportCompiler compiler) {
        this.definitions = definitions;
        this.compiler = compiler;
    }

    public TemplatePackage export(ReportActor actor, String id) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var definition = definitions.detail(current, id);
        var dsl = definitions.parseDraft(definition.json());
        var binding = definitions.bind(current, dsl);
        var portable = portable(dsl, dsl.code(), dsl.title(), null);
        var dependency =
                new Dependency(
                        dsl.datasetCode(),
                        binding.version().providerVersion(),
                        binding.version().checksum());
        var content = new Content(1, binding.engine().engineVersion(), portable, dependency);
        var checksum = ReportDefinitionService.hash(compiler.json(content));
        definitions.detail(definitions.resolve(current.tenantId(), current.userId()), id);
        definitions.bind(definitions.resolve(current.tenantId(), current.userId()), dsl);
        return new TemplatePackage(1, content.engine(), portable, dependency, checksum);
    }

    public Definition copy(
            ReportActor actor, String source, String code, String title, String catalog) {
        var pack = export(actor, source);
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var definition = definitions.detail(current, source);
        var dsl = definitions.parseDraft(definition.json());
        return importDraft(current, pack, dsl.datasetVersionId(), code, title, catalog);
    }

    public Definition importDraft(
            ReportActor actor,
            TemplatePackage pack,
            String datasetVersion,
            String code,
            String title,
            String catalog) {
        if (pack == null
                || pack.formatVersion() != 1
                || pack.dsl() == null
                || pack.dependency() == null
                || pack.checksum() == null) throw ReportFault.invalid("模板包格式无效");
        var content =
                new Content(pack.formatVersion(), pack.engine(), pack.dsl(), pack.dependency());
        if (!ReportDefinitionService.hash(compiler.json(content)).equals(pack.checksum()))
            throw new ReportFault("PACKAGE_INVALID", "模板包校验失败", 400);
        if (pack.dsl().datasetVersionId() != null
                || !pack.dependency().code().equals(pack.dsl().datasetCode()))
            throw ReportFault.invalid("模板包不可携带原租户数据集标识");
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, "report:definition:add");
        var dsl = portable(pack.dsl(), code, title, datasetVersion);
        var binding = definitions.bind(current, dsl);
        if (!binding.version().provider().equals(pack.dependency().code())
                || binding.version().providerVersion() != pack.dependency().providerVersion()
                || !binding.version().checksum().equals(pack.dependency().checksum())
                || !binding.engine().engineVersion().equals(pack.engine()))
            throw new ReportFault("DEPENDENCY_MISMATCH", "目标契约或引擎与模板包不匹配，请显式使用兼容契约", 409);
        current = definitions.resolve(current.tenantId(), current.userId());
        definitions.bind(current, dsl);
        return definitions.create(current, dsl, catalog);
    }

    private static ReportDsl portable(ReportDsl source, String code, String title, String version) {
        return new ReportDsl(
                source.schemaVersion(),
                code,
                title,
                source.kind(),
                source.datasetCode(),
                version,
                source.columns(),
                source.parameters().stream()
                        .map(
                                p ->
                                        new ReportDsl.Parameter(
                                                p.name(),
                                                p.label(),
                                                p.type(),
                                                p.required(),
                                                p.type().equals("ID") ? null : p.defaultValue()))
                        .toList(),
                source.page(),
                source.headerRows(),
                source.footerRows(),
                source.summaries(),
                source.repeatHeader());
    }
}
