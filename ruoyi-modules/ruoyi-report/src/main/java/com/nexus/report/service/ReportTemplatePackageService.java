package com.nexus.report.service;

import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.Definition;
import com.nexus.report.engine.ReportCompiler;

import java.util.*;

/** 可移植模板包不包含租户授权、业务标识默认值或运行产物。 */
public final class ReportTemplatePackageService {
    /**
     * Dependency，负责当前模块对应的契约或处理职责。
     *
     * @param code 当前契约的code配置或状态
     * @param providerVersion 当前契约的providerVersion配置或状态
     * @param checksum 实际内容的SHA-256校验和
     */
    public record Dependency(String code, int providerVersion, String checksum) {}

    /**
     * 模板模板包，负责当前模块对应的契约或处理职责。
     *
     * @param formatVersion 契约格式版本
     * @param engine 当前契约的engine配置或状态
     * @param dsl 不可变的报表定义
     * @param dependency 当前契约的dependency配置或状态
     * @param checksum 实际内容的SHA-256校验和
     */
    public record TemplatePackage(
            int formatVersion,
            String engine,
            ReportDsl dsl,
            Dependency dependency,
            String checksum) {}

    /**
     * Content，负责当前模块对应的契约或处理职责。
     *
     * @param formatVersion 契约格式版本
     * @param engine 当前契约的engine配置或状态
     * @param dsl 不可变的报表定义
     * @param dependency 当前契约的dependency配置或状态
     */
    private record Content(
            int formatVersion, String engine, ReportDsl dsl, Dependency dependency) {}

    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /** 报表编译器。 */
    private final ReportCompiler compiler;

    /**
     * 初始化报表模板模板包服务并保存所需依赖或不可变值。
     *
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     */
    public ReportTemplatePackageService(
            ReportDefinitionService definitions, ReportCompiler compiler) {
        this.definitions = definitions;
        this.compiler = compiler;
    }

    /**
     * 导出不包含租户授权和运行数据的模板包。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
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

    /**
     * 复制已授权定义并清除业务标识默认值。
     *
     * @param actor 当前服务端身份
     * @param source 来源定义或资源
     * @param code 业务编码
     * @param title 当前操作的 {@code title} 输入
     * @param catalog 当前操作的 {@code catalog} 输入
     * @return 当前处理返回的固定结果
     */
    public Definition copy(
            ReportActor actor, String source, String code, String title, String catalog) {
        var pack = export(actor, source);
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var definition = definitions.detail(current, source);
        var dsl = definitions.parseDraft(definition.json());
        return importDraft(current, pack, dsl.datasetVersionId(), code, title, catalog);
    }

    /**
     * 校验依赖和包完整性后创建本租户草稿。
     *
     * @param actor 当前服务端身份
     * @param pack 当前操作的 {@code pack} 输入
     * @param datasetVersion 当前操作的 {@code datasetVersion} 输入
     * @param code 业务编码
     * @param title 当前操作的 {@code title} 输入
     * @param catalog 当前操作的 {@code catalog} 输入
     * @return 当前处理返回的固定结果
     */
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

    /**
     * 完成 {@code portable} 对应的报表模板模板包服务处理。
     *
     * @param source 来源定义或资源
     * @param code 业务编码
     * @param title 当前操作的 {@code title} 输入
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
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
