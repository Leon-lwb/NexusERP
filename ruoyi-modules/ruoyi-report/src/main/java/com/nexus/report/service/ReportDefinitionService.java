package com.nexus.report.service;

import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.dataset.ReportDatasetResolver;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.version.*;

import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** 管理报表生命周期和资源权限，业务事实仅通过固定版本数据提供者读取。 */
public class ReportDefinitionService {
    /** 租户化元数据或私有存储。 */
    private final JdbcReportStore store;

    /** 代码契约注册表。 */
    private final ReportProviderRegistry registry;

    /** 代码和目录查询的统一固定版本解析器。 */
    private final ReportDatasetResolver datasetResolver;

    /** 当前授权解析接口。 */
    private final ReportAuthorizationPort authorization;

    /** 报表编译器。 */
    private final ReportCompiler compiler;

    /** 固定版本的报表执行器注册表。 */
    private final ReportEngineRegistry engines;

    /** 编译、运行与交付各阶段的耗时观测。 */
    private final com.nexus.report.runtime.ReportRuntimeMetrics metrics;

    /**
     * 初始化报表定义服务并保存所需依赖或不可变值。
     *
     * @param store 租户化元数据或私有存储
     * @param registry 代码契约注册表
     * @param authorization 当前授权解析接口
     * @param compiler 报表编译器
     */
    public ReportDefinitionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportAuthorizationPort authorization,
            ReportCompiler compiler) {
        this(store, registry, authorization, compiler, ReportEngineRegistry.v1(compiler));
    }

    /**
     * 初始化报表定义服务并保存所需依赖或不可变值。
     *
     * @param store 租户化元数据或私有存储
     * @param registry 代码契约注册表
     * @param authorization 当前授权解析接口
     * @param compiler 报表编译器
     * @param engines 当前操作的 {@code engines} 输入
     */
    public ReportDefinitionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportAuthorizationPort authorization,
            ReportCompiler compiler,
            ReportEngineRegistry engines) {
        this(
                store,
                registry,
                authorization,
                compiler,
                engines,
                com.nexus.report.runtime.ReportRuntimeMetrics.local());
    }

    /**
     * 初始化报表定义服务并保存所需依赖或不可变值。
     *
     * @param store 租户化元数据或私有存储
     * @param registry 代码契约注册表
     * @param authorization 当前授权解析接口
     * @param compiler 报表编译器
     * @param engines 当前操作的 {@code engines} 输入
     * @param metrics 当前操作的 {@code metrics} 输入
     */
    public ReportDefinitionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportAuthorizationPort authorization,
            ReportCompiler compiler,
            ReportEngineRegistry engines,
            com.nexus.report.runtime.ReportRuntimeMetrics metrics) {
        this(
                store,
                registry,
                authorization,
                compiler,
                engines,
                metrics,
                new ReportDatasetResolver(registry));
    }

    /**
     * 注入固定查询解析器，不改变既有代码提供者构造兼容性。
     *
     * @param store 逻辑存储标识
     * @param registry 当前契约的registry配置或状态
     * @param authorization 当前契约的authorization配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @param engines 当前契约的engines配置或状态
     * @param metrics 当前契约的metrics配置或状态
     * @param datasetResolver 当前契约的datasetResolver配置或状态
     */
    public ReportDefinitionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportAuthorizationPort authorization,
            ReportCompiler compiler,
            ReportEngineRegistry engines,
            com.nexus.report.runtime.ReportRuntimeMetrics metrics,
            ReportDatasetResolver datasetResolver) {
        this.store = store;
        this.registry = registry;
        this.authorization = authorization;
        this.compiler = compiler;
        this.engines = engines;
        this.metrics = metrics;
        this.datasetResolver = datasetResolver;
    }

    /**
     * 精确解析当前租户绑定的数据集版本，统一检查查询依赖。
     *
     * @param actor 执行用户
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportDatasetProvider resolveDataset(ReportActor actor, DatasetVersion version) {
        return datasetResolver.resolve(actor, version);
    }

    /**
     * 使用草稿格式对应执行器解析定义。
     *
     * @param text 当前操作的 {@code text} 输入
     * @return 当前处理返回的固定结果
     */
    public ReportDsl parseDraft(String text) {
        var node = compiler.read(text, com.fasterxml.jackson.databind.JsonNode.class);
        var schema = node == null ? null : node.get("schemaVersion");
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt())
            throw ReportFault.invalid("报表格式版本无效");
        return engines.draft(schema.intValue()).parse(text);
    }

    /**
     * 列出已安装执行器的格式与能力。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<ReportEngineRegistry.Capability> capabilities(ReportActor actor) {
        require(actor, "report:definition:list");
        return engines.capabilities();
    }

    /**
     * 绑定，负责当前模块对应的契约或处理职责。
     *
     * @param compiled 当前契约的compiled配置或状态
     * @param dataset 当前契约的dataset配置或状态
     * @param version 当前契约的version配置或状态
     * @param provider 当前契约的provider配置或状态
     * @param engine 当前契约的engine配置或状态
     */
    public record Binding(
            CompiledReport compiled,
            Dataset dataset,
            DatasetVersion version,
            ReportDatasetProvider provider,
            ReportEngineExecutor engine) {}

    /**
     * 数据集View，负责当前模块对应的契约或处理职责。
     *
     * @param dataset 当前契约的dataset配置或状态
     * @param descriptor 当前契约的descriptor配置或状态
     * @param canManage 当前契约的canManage配置或状态
     */
    public record DatasetView(Dataset dataset, DatasetDescriptor descriptor, boolean canManage) {}

    /**
     * DraftPreview，负责当前模块对应的契约或处理职责。
     *
     * @param rows 精确类型的数据行
     * @param total 当前契约的total配置或状态
     * @param dataTime 当前契约的dataTime配置或状态
     * @param summaries 当前契约的summaries配置或状态
     * @param plan 当前契约的plan配置或状态
     */
    public record DraftPreview(
            List<Map<String, TypedValue>> rows,
            long total,
            String dataTime,
            List<ReportCalculator.SummaryValue> summaries,
            RenderPlan plan) {}

    /**
     * 重新解析当前账户和业务权限。
     *
     * @param tenant 当前租户标识
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public ReportActor resolve(String tenant, long actor) {
        ReportActor current = authorization.resolve(tenant, actor);
        if (current == null || !tenant.equals(current.tenantId()) || current.userId() != actor)
            throw ReportFault.denied();
        return current;
    }

    /**
     * 检查当前操作所需权限或版本。
     *
     * @param actor 当前服务端身份
     * @param permission 待核对权限
     */
    public static void require(ReportActor actor, String permission) {
        if (!actor.has(permission)) throw ReportFault.denied();
    }

    /**
     * 完成 {@code list} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<Definition> list(ReportActor actor) {
        require(actor, "report:definition:list");
        return store.definitions(actor).stream()
                .map(
                        d ->
                                new Definition(
                                        d.id(),
                                        d.tenant(),
                                        d.code(),
                                        d.name(),
                                        d.kind(),
                                        d.catalogId(),
                                        null,
                                        d.revision(),
                                        d.publishedVersion(),
                                        d.status(),
                                        d.owner(),
                                        d.updatedAt()))
                .toList();
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param action 资源操作
     * @param operation 当前操作的 {@code operation} 输入
     * @return 当前处理返回的固定结果
     */
    public Definition definition(ReportActor actor, String id, String action, String operation) {
        require(actor, operation);
        var d =
                store.definition(actor.tenantId(), id)
                        .orElseThrow(ReportDefinitionService::missing);
        if (d.status().equals("ARCHIVED")
                || !store.allowed(actor, "REPORT", d.id(), d.owner(), action))
            throw ReportFault.denied();
        return d;
    }

    /**
     * Business模板，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param name 契约名称
     * @param kind 已发布报表的类型
     * @param versionId 绑定的不可变发布版本标识
     */
    public record BusinessTemplate(String id, String name, ReportDsl.Kind kind, String versionId) {
        /**
         * 保留原有调用方仅提供标识和名称的构造入口。
         *
         * @param id 报表标识
         * @param name 报表名称
         */
        public BusinessTemplate(String id, String name) {
            this(id, name, null, null);
        }
    }

    /**
     * 列出业务场景可运行的已发布模板。
     *
     * @param actor 当前服务端身份
     * @param code 业务编码
     * @return 当前处理返回的固定结果
     */
    public List<BusinessTemplate> businessTemplates(ReportActor actor, String code) {
        var current = resolve(actor.tenantId(), actor.userId());
        require(current, "report:runtime:preview");
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw ReportFault.invalid("数据集编码无效");
        var result = new ArrayList<BusinessTemplate>();
        for (var definition : store.definitions(current)) {
            if (!definition.status().equals("PUBLISHED")) continue;
            try {
                var version = published(current, definition.id(), "VIEW", "report:runtime:preview");
                var dsl = versionDsl(version);
                if (!dsl.datasetCode().equals(code)) continue;
                bindVersion(current, version);
                result.add(new BusinessTemplate(definition.id(), definition.name(), dsl.kind(), version.id()));
            } catch (ReportFault denied) {
                if (!Set.of("SCOPE_DENIED", "VERSION_UNSUPPORTED", "ENGINE_DISABLED")
                        .contains(denied.code())) throw denied;
            }
        }
        return List.copyOf(result);
    }

    /**
     * 读取可编辑定义详情。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Definition detail(ReportActor actor, String id) {
        return definition(actor, id, "EDIT", "report:definition:edit");
    }

    /**
     * 列出已安装且具备业务权限的提供者。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<DatasetDescriptor> providers(ReportActor actor) {
        require(actor, "report:dataset:manage");
        return registry.latest().stream()
                .map(ReportDatasetProvider::descriptor)
                .filter(d -> actor.has(d.permission()))
                .map(d -> masked(d, actor))
                .toList();
    }

    /**
     * 列出当前身份可见的数据集。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<DatasetView> datasets(ReportActor actor) {
        var current = resolve(actor.tenantId(), actor.userId());
        require(current, "report:dataset:list");
        var result = new ArrayList<DatasetView>();
        for (var dataset : store.datasets(current)) {
            if (dataset.publishedVersion() == null) continue;
            try {
                var version =
                        store.datasetVersion(current.tenantId(), dataset.publishedVersion())
                                .orElseThrow(ReportDefinitionService::missing);
                var descriptor = resolveDataset(current, version).descriptor();
                if (!current.has(descriptor.permission())) continue;
                result.add(
                        new DatasetView(
                                dataset,
                                masked(descriptor, current),
                                current.has("report:dataset:manage")
                                        && store.allowed(
                                                current,
                                                "DATASET",
                                                dataset.id(),
                                                dataset.owner(),
                                                "EDIT")));
            } catch (ReportFault failure) {
                if (!Set.of("SCOPE_DENIED", "QUERY_OFFLINE").contains(failure.code()))
                    throw failure;
            }
        }
        return List.copyOf(result);
    }

    /**
     * 按当前字段权限投影契约描述。
     *
     * @param d 当前操作的 {@code d} 输入
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    private static DatasetDescriptor masked(DatasetDescriptor d, ReportActor actor) {
        var fields =
                d.fields().stream()
                        .filter(
                                f ->
                                        f.permission() == null
                                                || f.permission().isBlank()
                                                || actor.has(f.permission()))
                        .toList();
        Set<String> visible = new HashSet<>();
        fields.forEach(f -> visible.add(f.name()));
        return new DatasetDescriptor(
                d.code(),
                d.name(),
                d.version(),
                d.permission(),
                d.document(),
                fields,
                d.parameters(),
                d.defaultColumns().stream().filter(visible::contains).toList());
    }

    /**
     * 登记已安装的代码契约版本。
     *
     * @param actor 当前服务端身份
     * @param code 业务编码
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    public Dataset registerDataset(ReportActor actor, String code, int version) {
        require(actor, "report:dataset:manage");
        var provider = version <= 0 ? registry.latest(code) : registry.get(code, version);
        var d = provider.descriptor();
        if (!actor.has(d.permission())) throw ReportFault.denied();
        var existing = store.datasetByCode(actor.tenantId(), code);
        if (existing.isPresent()
                && !store.allowed(
                        actor, "DATASET", existing.get().id(), existing.get().owner(), "EDIT"))
            throw ReportFault.denied();
        String json = compiler.json(d);
        var result = store.registerDataset(actor, d, json, hash(json));
        store.audit(actor, result.id(), "DATASET_PUBLISH", "SUCCEEDED", null);
        return result;
    }

    /**
     * 完成 {@code datasetState} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     */
    public void datasetState(ReportActor actor, String id, String state) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Dataset dataset = managedDataset(current, id);
        datasetState(current, id, state, dataset.status());
    }

    /**
     * 完成 {@code datasetState} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     * @param expectedStatus 当前操作的 {@code expectedStatus} 输入
     */
    public void datasetState(ReportActor actor, String id, String state, String expectedStatus) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Dataset dataset = managedDataset(current, id);
        if (expectedStatus == null || !dataset.status().equals(expectedStatus))
            throw revisionConflict();
        store.datasetState(current, id, state, expectedStatus);
        store.audit(current, id, "DATASET_STATE", "SUCCEEDED", null);
    }

    /**
     * 绑定固定数据集及执行器并编译。
     *
     * @param actor 当前服务端身份
     * @param dsl 报表定义
     * @return 当前处理返回的固定结果
     */
    public Binding bind(ReportActor actor, ReportDsl dsl) {
        return bind(actor, dsl, engines.draft(dsl.schemaVersion()));
    }

    /**
     * 按发布版本绑定固定数据集及执行器。
     *
     * @param actor 当前服务端身份
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    /**
     * 服务端来源约束读取固定历史报告，并实时检查预览、导出及上线状态。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @return 当前受控处理的固定结果
     */
    public Binding fixedBinding(ReportActor actor, String versionId) {
        var current = resolve(actor.tenantId(), actor.userId());
        var version =
                store.version(current.tenantId(), versionId)
                        .orElseThrow(ReportDefinitionService::missing);
        var definition = definition(current, version.reportId(), "VIEW", "report:runtime:preview");
        definition(current, version.reportId(), "EXPORT", "report:runtime:export");
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "固定报告已下线", 409);
        var binding = bindVersion(current, version);
        requireDatasetExport(current, binding);
        return binding;
    }

    /**
     * 解析不可变发布版本，重新验证当前身份、资源授权、字段权限与业务范围。
     *
     * @param actor 执行用户
     * @param version 当前契约的version配置或状态
     * @return 当前受控操作的固定结果
     */
    public Binding bindVersion(ReportActor actor, Version version) {
        return bind(
                actor, versionDsl(version), engines.require(version.schema(), version.engine()));
    }

    /**
     * 绑定固定数据集及执行器并编译。
     *
     * @param actor 当前服务端身份
     * @param dsl 报表定义
     * @param executor 当前操作的 {@code executor} 输入
     * @return 当前处理返回的固定结果
     */
    private Binding bind(ReportActor actor, ReportDsl dsl, ReportEngineExecutor executor) {
        var version =
                store.datasetVersion(actor.tenantId(), dsl.datasetVersionId())
                        .orElseThrow(ReportDefinitionService::missing);
        var dataset =
                store.dataset(actor.tenantId(), version.datasetId())
                        .orElseThrow(ReportDefinitionService::missing);
        if (!dataset.code().equals(dsl.datasetCode())
                || !dataset.status().equals("ACTIVE")
                || !store.allowed(actor, "DATASET", dataset.id(), dataset.owner(), "VIEW"))
            throw ReportFault.denied();
        var provider = resolveDataset(actor, version);
        var descriptor = provider.descriptor();
        if (!hash(compiler.json(descriptor)).equals(version.checksum()))
            throw new ReportFault("VERSION_UNSUPPORTED", "数据集契约已变化，请发布新数据集版本并重新绑定");
        return new Binding(
                metrics.measure(
                        com.nexus.report.runtime.ReportRuntimeMetrics.Phase.COMPILE,
                        () -> executor.compile(dsl, descriptor, actor)),
                dataset,
                version,
                provider,
                executor);
    }

    /**
     * 完成 {@code requireDatasetExport} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param binding 当前操作的 {@code binding} 输入
     */
    public void requireDatasetExport(ReportActor actor, Binding binding) {
        if (!store.allowed(
                actor, "DATASET", binding.dataset().id(), binding.dataset().owner(), "EXPORT"))
            throw ReportFault.denied();
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param actor 当前服务端身份
     * @param dsl 报表定义
     * @param catalog 当前操作的 {@code catalog} 输入
     * @return 当前处理返回的固定结果
     */
    public Definition create(ReportActor actor, ReportDsl dsl, String catalog) {
        require(actor, "report:definition:add");
        bind(actor, dsl);
        var result =
                store.createDefinition(
                        actor,
                        dsl.code(),
                        dsl.title(),
                        dsl.kind().name(),
                        catalog,
                        compiler.json(dsl));
        store.audit(actor, result.id(), "CREATE", "SUCCEEDED", null);
        return result;
    }

    /**
     * 完成 {@code save} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param revision 预期草稿修订
     * @param dsl 报表定义
     * @return 当前处理返回的固定结果
     */
    public Definition save(ReportActor actor, String id, long revision, ReportDsl dsl) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = definition(current, id, "EDIT", "report:definition:edit");
        checkRevision(definition, revision);
        identity(definition, dsl);
        bind(current, dsl);
        current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "EDIT", "report:definition:edit"), revision);
        var result = store.saveDraft(current, id, revision, dsl.title(), compiler.json(dsl));
        store.audit(current, id, "SAVE", "SUCCEEDED", null);
        return result;
    }

    /**
     * 检查定义或查询是否满足受限契约。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param dsl 报表定义
     * @return 当前处理返回的固定结果
     */
    public Map<String, Object> validate(ReportActor actor, String id, ReportDsl dsl) {
        if (id == null) require(actor, "report:definition:add");
        else definition(actor, id, "EDIT", "report:definition:edit");
        var binding = bind(actor, dsl);
        return Map.of(
                "valid",
                true,
                "schemaVersion",
                1,
                "requiredFields",
                binding.compiled.requiredFields(),
                "computedOrder",
                binding.compiled.computedOrder());
    }

    /**
     * 在修订与授权检查后创建不可变发布版本。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param revision 预期草稿修订
     * @return 当前处理返回的固定结果
     */
    public Version publish(ReportActor actor, String id, long revision) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = definition(current, id, "PUBLISH", "report:definition:publish");
        checkRevision(definition, revision);
        ReportDsl dsl = parseDraft(definition.json());
        bind(current, dsl);
        current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "PUBLISH", "report:definition:publish"), revision);
        String text = compiler.json(dsl);
        var executor = bind(current, dsl).engine();
        var result =
                store.publish(
                        current,
                        id,
                        revision,
                        text,
                        hash(text),
                        dsl.datasetVersionId(),
                        executor.schemaVersion(),
                        executor.engineVersion());
        store.audit(current, id, "PUBLISH", "SUCCEEDED", "{\"version\":" + result.number() + "}");
        return result;
    }

    /**
     * 完成 {@code versions} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public List<Version> versions(ReportActor actor, String id) {
        definition(actor, id, "VIEW", "report:definition:list");
        return store.versions(actor.tenantId(), id);
    }

    /**
     * 完成 {@code rollback} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param version 固定版本
     */
    public void rollback(ReportActor actor, String id, String version) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = definition(current, id, "PUBLISH", "report:definition:publish");
        rollback(current, id, version, definition.revision());
    }

    /**
     * 完成 {@code rollback} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param version 固定版本
     * @param revision 预期草稿修订
     */
    public void rollback(ReportActor actor, String id, String version, long revision) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "PUBLISH", "report:definition:publish"), revision);
        Version target =
                store.version(current.tenantId(), version)
                        .orElseThrow(ReportDefinitionService::missing);
        if (!id.equals(target.reportId())) throw missing();
        bindVersion(current, target);
        current = resolve(actor.tenantId(), actor.userId());
        checkRevision(definition(current, id, "PUBLISH", "report:definition:publish"), revision);
        store.rollback(current, id, version, revision);
        store.audit(current, id, "ROLLBACK", "SUCCEEDED", null);
    }

    /**
     * 按修订和权限执行生命周期迁移。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     */
    public void state(ReportActor actor, String id, String state) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Definition definition = stateDefinition(current, id, state);
        state(current, id, state, definition.revision());
    }

    /**
     * 按修订和权限执行生命周期迁移。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     * @param revision 预期草稿修订
     */
    public void state(ReportActor actor, String id, String state, long revision) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        checkRevision(stateDefinition(current, id, state), revision);
        store.definitionState(current, id, state, revision);
        store.audit(current, id, state, "SUCCEEDED", null);
    }

    /**
     * 完成 {@code acl} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param type 当前操作的 {@code type} 输入
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public List<Grant> acl(ReportActor actor, String type, String id) {
        resourceManage(actor, type, id);
        return store.acl(actor.tenantId(), type, id);
    }

    /**
     * 完成 {@code acl} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param type 当前操作的 {@code type} 输入
     * @param id 资源标识
     * @param grants 当前操作的 {@code grants} 输入
     */
    public void acl(ReportActor actor, String type, String id, List<Grant> grants) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        resourceManage(current, type, id);
        if (grants == null || grants.size() > 200) throw ReportFault.capacity();
        for (Grant grant : grants) {
            if (grant == null
                    || grant.subjectType() == null
                    || grant.action() == null
                    || grant.subjectId() == null
                    || !Set.of("USER", "ROLE", "DEPT").contains(grant.subjectType())
                    || !Set.of("VIEW", "EDIT", "PUBLISH", "EXPORT", "MANAGE")
                            .contains(grant.action())) throw ReportFault.invalid("授权规则无效");
            long subject;
            try {
                subject = Long.parseLong(grant.subjectId());
            } catch (RuntimeException invalid) {
                throw ReportFault.invalid("授权主体标识无效");
            }
            if (subject <= 0) throw ReportFault.invalid("授权主体标识无效");
            if (!authorization.subjectExists(current.tenantId(), grant.subjectType(), subject))
                throw ReportFault.denied();
        }
        current = resolve(actor.tenantId(), actor.userId());
        resourceManage(current, type, id);
        store.replaceAcl(current, type, id, grants);
        store.audit(current, id, "ACL_CHANGE", "SUCCEEDED", null);
    }

    /**
     * 完成 {@code resourceManage} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param type 当前操作的 {@code type} 输入
     * @param id 资源标识
     */
    private void resourceManage(ReportActor actor, String type, String id) {
        require(actor, "report:acl:manage");
        if ("REPORT".equals(type)) definition(actor, id, "MANAGE", "report:acl:manage");
        else if ("DATASET".equals(type)) {
            Dataset dataset =
                    store.dataset(actor.tenantId(), id)
                            .orElseThrow(ReportDefinitionService::missing);
            if (dataset.status().equals("ARCHIVED")
                    || !store.allowed(actor, type, id, dataset.owner(), "MANAGE"))
                throw ReportFault.denied();
        } else if (Set.of("QUERY", "DASHBOARD", "SUBSCRIPTION", "ASSET", "BATCH").contains(type)) {
            if (!store.managedResource(actor, type, id)) throw ReportFault.denied();
        } else throw ReportFault.invalid("资源类型无效");
    }

    /**
     * 完成 {@code catalogs} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<Catalog> catalogs(ReportActor actor) {
        require(actor, "report:definition:list");
        return store.catalogs(actor.tenantId());
    }

    /**
     * 完成 {@code catalog} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param parent 当前操作的 {@code parent} 输入
     * @param name 当前操作的 {@code name} 输入
     * @return 当前处理返回的固定结果
     */
    public Catalog catalog(ReportActor actor, String parent, String name) {
        require(actor, "report:catalog:manage");
        if (name == null || name.isBlank() || name.length() > 100)
            throw ReportFault.invalid("目录名称长度为 1–100");
        return store.createCatalog(actor, parent, name);
    }

    /**
     * 完成 {@code audits} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<Map<String, Object>> audits(ReportActor actor) {
        require(actor, "report:audit:query");
        return store.audits(actor.tenantId());
    }

    /**
     * 提供与固定数据集契约一致的有效默认提示，不改写已发布定义或提前计算日期。
     *
     * @param binding 已授权且编译完成的固定版本绑定
     * @return 模板默认值优先、数据集默认值兜底的独立参数列表
     */
    public List<ReportDsl.Parameter> effectiveParameters(Binding binding) {
        return binding.compiled().dsl().parameters().stream()
                .map(
                        parameter -> {
                            var registered =
                                    binding.compiled().descriptor().parameters().stream()
                                            .filter(item -> item.name().equals(parameter.name()))
                                            .findFirst()
                                            .orElseThrow(() -> ReportFault.invalid("报表参数与数据集不匹配"));
                            String value = parameter.defaultValue();
                            if (value == null || value.isBlank()) value = registered.defaultValue();
                            return new ReportDsl.Parameter(
                                    parameter.name(),
                                    parameter.label(),
                                    parameter.type(),
                                    parameter.required(),
                                    value);
                        })
                .toList();
    }

    /**
     * 完成 {@code parameters} 对应的报表定义服务处理。
     *
     * @param binding 当前操作的 {@code binding} 输入
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    public Map<String, Object> parameters(Binding binding, Map<String, Object> input) {
        Set<String> declared = new HashSet<>();
        binding.compiled().dsl().parameters().forEach(parameter -> declared.add(parameter.name()));
        if (input != null && !declared.containsAll(input.keySet()))
            throw new DatasetAccessException("INVALID_PARAMETER", "包含未声明的报表参数");
        var values = new LinkedHashMap<String, Object>();
        for (var parameter : binding.compiled().dsl().parameters())
            if (parameter.defaultValue() != null && !parameter.defaultValue().isBlank()) {
                String value = parameter.defaultValue();
                if (parameter.type().equals("DATE") && value.equals("TODAY"))
                    value = java.time.LocalDate.now().toString();
                if (parameter.type().equals("DATE") && value.equals("MONTHS_AGO_6"))
                    value = java.time.LocalDate.now().minusMonths(6).toString();
                values.put(parameter.name(), value);
            }
        if (input != null)
            input.forEach(
                    (name, value) -> {
                        if (value != null && !(value instanceof String text && text.isBlank()))
                            values.put(name, value);
                    });
        var descriptor = binding.provider().descriptor();
        for (var parameter : binding.compiled().dsl().parameters()) {
            var registered =
                    descriptor.parameters().stream()
                            .filter(item -> item.name().equals(parameter.name()))
                            .findFirst()
                            .orElseThrow(() -> ReportFault.invalid("报表参数与数据集不匹配"));
            if (parameter.required()
                    && !values.containsKey(parameter.name())
                    && (registered.defaultValue() == null || registered.defaultValue().isBlank()))
                throw new ReportFault("INVALID_PARAMETER", "请先填写必填条件：" + registered.label(), 400);
        }
        return DatasetQueryValidator.parameters(descriptor, values);
    }

    /**
     * 完成 {@code preview} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param dsl 报表定义
     * @param input 待校验输入
     * @param font 可信私有字体路径
     * @return 当前处理返回的固定结果
     */
    public DraftPreview preview(
            ReportActor actor, String id, ReportDsl dsl, Map<String, Object> input, String font) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        previewPermission(current, id, dsl);
        Binding binding = bind(current, dsl);
        String fingerprint = fingerprint(current, binding);
        Map<String, Object> parameters = parameters(binding, input);
        var result =
                binding.provider()
                        .query(
                                binding.compiled().context(current),
                                new DatasetQuery(
                                        parameters,
                                        binding.compiled().requiredFields(),
                                        1,
                                        200,
                                        false,
                                        null,
                                        false));
        if (result.rows().size() > 200) throw ReportFault.capacity();
        var data = binding.engine().calculate(binding.compiled(), result.rows(), parameters);
        RenderPlan plan =
                dsl.kind() == ReportDsl.Kind.DOCUMENT
                        ? binding.engine()
                                .layout(
                                        binding.compiled(),
                                        data,
                                        parameters,
                                        FontSupport.find(font))
                        : null;
        current = resolve(actor.tenantId(), actor.userId());
        previewPermission(current, id, dsl);
        if (!fingerprint.equals(fingerprint(current, bind(current, dsl)))) throw scopeChanged();
        return new DraftPreview(
                project(data.rows(), dsl),
                result.total(),
                result.dataTime().toString(),
                data.summaries(),
                plan);
    }

    /**
     * 完成 {@code sample} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    public DatasetResult sample(ReportActor actor, String id, Map<String, Object> input) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        Dataset dataset = sampleDataset(current, id);
        DatasetVersion version =
                store.datasetVersion(current.tenantId(), dataset.publishedVersion())
                        .orElseThrow(ReportDefinitionService::missing);
        if (!version.datasetId().equals(dataset.id())) throw missing();
        var provider = resolveDataset(actor, version);
        DatasetDescriptor descriptor = provider.descriptor();
        if (!hash(compiler.json(descriptor)).equals(version.checksum()))
            throw new ReportFault("VERSION_UNSUPPORTED", "数据集契约已变化");
        Set<String> names = new LinkedHashSet<>(masked(descriptor, current).defaultColumns());
        if (names.isEmpty()) throw ReportFault.denied();
        var context = sampleContext(current, descriptor, names);
        String fingerprint = scopeFingerprint(current, version, names, context.scopes());
        var parameters = DatasetQueryValidator.parameters(descriptor, input);
        var result =
                provider.query(
                        context, new DatasetQuery(parameters, names, 1, 50, false, null, false));
        if (result.rows().size() > 50) throw ReportFault.capacity();
        current = resolve(actor.tenantId(), actor.userId());
        sampleDataset(current, id);
        var recheck = sampleContext(current, descriptor, names);
        if (!fingerprint.equals(scopeFingerprint(current, version, names, recheck.scopes())))
            throw scopeChanged();
        List<Map<String, Object>> rows =
                result.rows().stream()
                        .map(
                                row -> {
                                    Map<String, Object> projected = new LinkedHashMap<>();
                                    names.forEach(name -> projected.put(name, row.get(name)));
                                    return projected;
                                })
                        .toList();
        return new DatasetResult(rows, result.total(), result.dataTime());
    }

    /**
     * 显式准备当前管理员有业务权限的初始模板。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<Definition> bootstrap(ReportActor actor) {
        ReportActor current = resolve(actor.tenantId(), actor.userId());
        require(current, "report:dataset:manage");
        require(current, "report:definition:add");
        require(current, "report:definition:publish");
        String catalog = store.ensureRootCatalog(current).id();
        List<Definition> results = new ArrayList<>();
        for (var provider : registry.latest()) {
            var descriptor = provider.descriptor();
            if (!current.has(descriptor.permission())) continue;
            Dataset dataset = registerDataset(current, descriptor.code(), descriptor.version());
            for (var dsl :
                    ReportTemplates.foundation(
                            masked(descriptor, current), dataset.publishedVersion())) {
                String code = dsl.code();
                var existing = store.definitionByCode(current.tenantId(), code);
                if (existing.isPresent()) {
                    addExistingTemplate(results, current, existing.get());
                    continue;
                }
                if (dsl.columns().isEmpty()) continue;
                Definition created;
                try {
                    created = create(current, dsl, catalog);
                } catch (DuplicateKeyException race) {
                    Definition canonical =
                            store.definitionByCode(current.tenantId(), code)
                                    .orElseThrow(ReportDefinitionService::revisionConflict);
                    addExistingTemplate(results, current, canonical);
                    continue;
                }
                publish(current, created.id(), created.revision());
                results.add(
                        store.definition(current.tenantId(), created.id())
                                .orElseThrow(ReportDefinitionService::missing));
            }
        }
        return List.copyOf(results);
    }

    /**
     * 完成 {@code addExistingTemplate} 对应的报表定义服务处理。
     *
     * @param results 当前操作的 {@code results} 输入
     * @param actor 当前服务端身份
     * @param definition 当前操作的 {@code definition} 输入
     */
    private void addExistingTemplate(
            List<Definition> results, ReportActor actor, Definition definition) {
        if (!definition.status().equals("ARCHIVED")
                && actor.has("report:definition:list")
                && store.allowed(actor, "REPORT", definition.id(), definition.owner(), "VIEW"))
            results.add(definition);
    }

    /**
     * 读取当前仍可使用的发布版本。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param action 资源操作
     * @param permission 待核对权限
     * @return 当前处理返回的固定结果
     */
    public Version published(ReportActor actor, String report, String action, String permission) {
        var definition = definition(actor, report, action, permission);
        if (!definition.status().equals("PUBLISHED") || definition.publishedVersion() == null)
            throw new ReportFault("REPORT_OFFLINE", "报表尚未发布或已经下线", 409);
        Version version =
                store.version(actor.tenantId(), definition.publishedVersion())
                        .orElseThrow(ReportDefinitionService::missing);
        if (!definition.id().equals(version.reportId())) throw missing();
        versionDsl(version);
        return version;
    }

    /**
     * 发布引用不可变，只使用其声明且已安装的兼容执行器。
     *
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportDsl versionDsl(Version version) {
        var executor = engines.require(version.schema(), version.engine());
        if (version.json() == null || !hash(version.json()).equals(version.checksum()))
            throw new ReportFault("VERSION_INVALID", "已发布报表校验失败", 409);
        var dsl = executor.parse(version.json());
        if (dsl.schemaVersion() != version.schema()
                || !Objects.equals(dsl.datasetVersionId(), version.datasetVersion()))
            throw new ReportFault("VERSION_INVALID", "已发布报表引用不一致", 409);
        return dsl;
    }

    /**
     * 完成 {@code project} 对应的报表定义服务处理。
     *
     * @param rows 待处理业务行
     * @param dsl 报表定义
     * @return 当前处理返回的固定结果
     */
    public static List<Map<String, TypedValue>> project(
            List<Map<String, TypedValue>> rows, ReportDsl dsl) {
        return rows.stream()
                .map(
                        row -> {
                            Map<String, TypedValue> result = new LinkedHashMap<>();
                            dsl.columns().forEach(c -> result.put(c.field(), row.get(c.field())));
                            return result;
                        })
                .toList();
    }

    /**
     * 生成当前身份与业务范围的稳定指纹。
     *
     * @param actor 当前服务端身份
     * @param binding 当前操作的 {@code binding} 输入
     * @return 当前处理返回的固定结果
     */
    public String fingerprint(ReportActor actor, Binding binding) {
        return scopeFingerprint(
                actor,
                binding.version(),
                binding.compiled().requiredFields(),
                binding.compiled().context(actor).scopes());
    }

    /**
     * 生成稳定的内容完整性摘要。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 完成 {@code managedDataset} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    private Dataset managedDataset(ReportActor actor, String id) {
        require(actor, "report:dataset:manage");
        Dataset dataset =
                store.dataset(actor.tenantId(), id).orElseThrow(ReportDefinitionService::missing);
        if (dataset.status().equals("ARCHIVED")
                || !store.allowed(actor, "DATASET", id, dataset.owner(), "EDIT"))
            throw ReportFault.denied();
        return dataset;
    }

    /**
     * 完成 {@code sampleDataset} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    private Dataset sampleDataset(ReportActor actor, String id) {
        require(actor, "report:dataset:list");
        Dataset dataset =
                store.dataset(actor.tenantId(), id).orElseThrow(ReportDefinitionService::missing);
        if (!dataset.status().equals("ACTIVE")
                || !store.allowed(actor, "DATASET", id, dataset.owner(), "VIEW"))
            throw ReportFault.denied();
        return dataset;
    }

    /**
     * 完成 {@code sampleContext} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param descriptor 可信数据集契约
     * @param names 当前操作的 {@code names} 输入
     * @return 当前处理返回的固定结果
     */
    private static ReportExecutionContext sampleContext(
            ReportActor actor, DatasetDescriptor descriptor, Set<String> names) {
        require(actor, descriptor.permission());
        List<ReportScope> scopes = new ArrayList<>();
        scopes.add(actor.scopeFor(descriptor.permission()));
        Set<String> permissions = new TreeSet<>();
        for (String name : names) {
            String permission = descriptor.field(name).permission();
            if (permission != null && !permission.isBlank()) permissions.add(permission);
        }
        for (String permission : permissions) {
            require(actor, permission);
            scopes.add(actor.scopeFor(permission));
        }
        return new ReportExecutionContext(actor, scopes);
    }

    /**
     * 完成 {@code stateDefinition} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param state 目标状态
     * @return 当前处理返回的固定结果
     */
    private Definition stateDefinition(ReportActor actor, String id, String state) {
        if (state == null || !Set.of("OFFLINE", "ARCHIVED").contains(state))
            throw ReportFault.invalid("报表状态无效");
        return definition(
                actor,
                id,
                state.equals("ARCHIVED") ? "EDIT" : "PUBLISH",
                state.equals("ARCHIVED")
                        ? "report:definition:remove"
                        : "report:definition:publish");
    }

    /**
     * 完成 {@code previewPermission} 对应的报表定义服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param dsl 报表定义
     */
    private void previewPermission(ReportActor actor, String id, ReportDsl dsl) {
        if (id == null) require(actor, "report:definition:add");
        else identity(definition(actor, id, "EDIT", "report:definition:edit"), dsl);
    }

    /**
     * 完成 {@code identity} 对应的报表定义服务处理。
     *
     * @param definition 当前操作的 {@code definition} 输入
     * @param dsl 报表定义
     */
    private static void identity(Definition definition, ReportDsl dsl) {
        if (dsl == null
                || dsl.kind() == null
                || !definition.code().equals(dsl.code())
                || !definition.kind().equals(dsl.kind().name()))
            throw ReportFault.invalid("已有报表编码和类型不可更改");
    }

    /**
     * 完成 {@code checkRevision} 对应的报表定义服务处理。
     *
     * @param definition 当前操作的 {@code definition} 输入
     * @param revision 预期草稿修订
     */
    private static void checkRevision(Definition definition, long revision) {
        if (definition.revision() != revision) throw revisionConflict();
    }

    /**
     * 生成固定业务范围的稳定指纹。
     *
     * @param actor 当前服务端身份
     * @param version 固定版本
     * @param fields 允许访问的字段
     * @param scopes 业务权限范围
     * @return 当前处理返回的固定结果
     */
    private String scopeFingerprint(
            ReportActor actor,
            DatasetVersion version,
            Set<String> fields,
            List<ReportScope> scopes) {
        var source = new TreeMap<String, Object>();
        source.put("tenant", actor.tenantId());
        source.put("actor", Long.toString(actor.userId()));
        source.put("department", Long.toString(actor.departmentId()));
        source.put("dataset", version.id());
        source.put("descriptor", version.checksum());
        var provider = resolveDataset(actor, version);
        if (!provider.executionIdentity().isEmpty()) {
            source.put("execution", provider.executionIdentity());
            var policiesByPermission = new TreeMap<String, ReportScope>();
            for (String permission : provider.requiredPermissions()) {
                require(actor, permission);
                policiesByPermission.put(permission, actor.scopeFor(permission));
            }
            source.put("entityPolicies", policiesByPermission);
        }
        source.put("fields", new TreeSet<>(fields));
        Set<String> policies = new TreeSet<>();
        for (ReportScope scope : scopes) {
            var policy = new TreeMap<String, Object>();
            policy.put("all", scope.all());
            if (!scope.all()) {
                policy.put("departments", new TreeSet<>(scope.departments()));
                policy.put("self", scope.self());
            }
            policies.add(compiler.json(policy));
        }
        source.put("scopes", policies);
        return hash(compiler.json(source));
    }

    /**
     * 完成 {@code revisionConflict} 对应的报表定义服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault revisionConflict() {
        return new ReportFault("REVISION_CONFLICT", "内容或状态已变化，请重新读取后操作", 409);
    }

    /**
     * 完成 {@code scopeChanged} 对应的报表定义服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault scopeChanged() {
        return new ReportFault("SCOPE_CHANGED", "权限范围已变化，请重新预览或运行报表", 403);
    }

    /**
     * 完成 {@code missing} 对应的报表定义服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault missing() {
        return new ReportFault("NOT_FOUND", "报表资源不存在或不可访问", 404);
    }
}
