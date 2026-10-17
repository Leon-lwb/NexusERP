package com.nexus.report.runtime;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.service.ReportDefinitionService;
import com.nexus.report.service.ReportDefinitionService.Binding;
import com.nexus.report.storage.ReportArtifactStore;

import java.time.Instant;
import java.util.*;

/** 每次交付重新检查固定版本、输入、数据范围及工作线程持有的租约。 */
public class ReportRuntimeService {
    /** 保存当前 {@code PREVIEW} 值。 */
    private static final String PREVIEW = "report:runtime:preview";

    /** 保存当前 {@code EXPORT} 值。 */
    private static final String EXPORT = "report:runtime:export";

    /** 租户化元数据或私有存储。 */
    private final JdbcReportStore store;

    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /** 报表编译器。 */
    private final ReportCompiler compiler;

    /** 保存当前 {@code snapshots} 值。 */
    private final ReportSnapshotStore snapshots;

    /** 保存当前 {@code plans} 值。 */
    private final ReportRenderPlanStore plans;

    /** 保存当前 {@code artifacts} 值。 */
    private final ReportArtifactStore artifacts;

    /** 保存当前 {@code properties} 值。 */
    private final ReportProperties properties;

    /** 待处理定义文本。 */
    private final ObjectMapper json;

    /** 编译、运行与交付各阶段的耗时观测。 */
    private ReportRuntimeMetrics metrics = ReportRuntimeMetrics.local();

    /**
     * 完成 {@code metrics} 对应的报表运行服务处理。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    /**
     * 已安装扩展结构时复用私有字体，历史内嵌字体计划仍可读取。
     *
     * @param fonts 当前契约的fonts配置或状态
     */
    public void fontResources(ReportFontResourceStore fonts) {
        plans.fonts(fonts);
    }

    /**
     * 执行metrics对应的受控处理。
     *
     * @param value 当前受控操作的value输入
     */
    public void metrics(ReportRuntimeMetrics value) {
        this.metrics = java.util.Objects.requireNonNull(value);
    }

    /**
     * 记录下载交付耗时与成功状态。
     *
     * @param success 当前操作的 {@code success} 输入
     * @param nanos 当前操作的 {@code nanos} 输入
     */
    public void recordDelivery(boolean success, long nanos) {
        metrics.record(ReportRuntimeMetrics.Phase.DELIVERY, success, nanos);
    }

    /**
     * 记录导出耗时与成功状态。
     *
     * @param success 当前操作的 {@code success} 输入
     * @param nanos 当前操作的 {@code nanos} 输入
     */
    public void recordExport(boolean success, long nanos) {
        metrics.record(ReportRuntimeMetrics.Phase.EXPORT, success, nanos);
    }

    /**
     * 初始化报表运行服务并保存所需依赖或不可变值。
     *
     * @param store 租户化元数据或私有存储
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     * @param snapshots 当前操作的 {@code snapshots} 输入
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param properties 当前操作的 {@code properties} 输入
     * @param json 待处理定义文本
     */
    public ReportRuntimeService(
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportCompiler compiler,
            ReportSnapshotStore snapshots,
            ReportArtifactStore artifacts,
            ReportProperties properties,
            ObjectMapper json) {
        this.store = store;
        this.definitions = definitions;
        this.compiler = compiler;
        this.snapshots = snapshots;
        this.artifacts = artifacts;
        this.properties = properties;
        this.plans = new ReportRenderPlanStore(artifacts, json);
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /**
     * 固定发布运行与交付服务，在每次读取时重新检查租户和业务范围。
     *
     * @param input 已规范化的参数值
     * @param store 逻辑存储标识
     * @param checksum 实际内容的SHA-256校验和
     * @param summaries 当前契约的summaries配置或状态
     * @param planChecksum 当前契约的planChecksum配置或状态
     * @param constraint 当前契约的constraint配置或状态
     */
    public record Payload(
            Map<String, Object> input,
            String store,
            String checksum,
            List<ReportCalculator.SummaryValue> summaries,
            String planChecksum,
            @com.fasterxml.jackson.annotation.JsonInclude(
                            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                    SourceConstraint constraint) {
        /**
         * 保留第一版运行配置构造方式。
         *
         * @param input 已规范化的参数值
         * @param store 逻辑存储标识
         * @param checksum 实际内容的SHA-256校验和
         * @param summaries 当前契约的summaries配置或状态
         * @param planChecksum 当前契约的planChecksum配置或状态
         */
        public Payload(
                Map<String, Object> input,
                String store,
                String checksum,
                List<ReportCalculator.SummaryValue> summaries,
                String planChecksum) {
            this(input, store, checksum, summaries, planChecksum, null);
        }

        /**
         * 初始化Payload并保存所需依赖或不可变值。
         *
         * @param input 待校验输入
         * @param store 租户化元数据或私有存储
         * @param checksum 预期完整性校验和
         * @param summaries 当前操作的 {@code summaries} 输入
         */
        public Payload(
                Map<String, Object> input,
                String store,
                String checksum,
                List<ReportCalculator.SummaryValue> summaries) {
            this(input, store, checksum, summaries, null);
        }

        /**
         * 初始化Payload并保存所需依赖或不可变值。
         *
         * @param input 待校验输入
         * @param store 租户化元数据或私有存储
         * @param checksum 预期完整性校验和
         * @param summaries 当前操作的 {@code summaries} 输入
         * @param planChecksum 当前操作的 {@code planChecksum} 输入
         * @param constraint 当前契约的constraint配置或状态
         */
        public Payload {
            input = parametersForStorage(input);
            summaries = List.copyOf(summaries == null ? List.of() : summaries);
        }
    }

    /**
     * 固定来源的附加资源约束，不是外部分享凭证。
     *
     * @param type 受控类型
     * @param resourceId 当前契约的resourceId配置或状态
     * @param versionId 不可变发布版本标识
     */
    public record SourceConstraint(String type, String resourceId, String versionId) {}

    /** 来源约束在每次预览、执行和下载重新校验，默认拒绝未安装约束处理器。 */
    private java.util.function.BiConsumer<ReportActor, SourceConstraint> constraintGuard =
            (actor, source) -> {
                throw new ReportFault("SOURCE_CONSTRAINT_UNAVAILABLE", "运行来源约束服务未安装", 409);
            };

    /**
     * 装配受控订阅来源校验器，不接收前端执行代码。
     *
     * @param guard 当前契约的guard配置或状态
     */
    public void constraints(java.util.function.BiConsumer<ReportActor, SourceConstraint> guard) {
        constraintGuard = Objects.requireNonNull(guard);
    }

    /**
     * 持久化数值参数为精确字符串，即使局部Jackson未配置大数序列化也不丢失精度。
     *
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    public static Map<String, Object> parametersForStorage(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>();
        if (input != null)
            input.forEach(
                    (name, value) ->
                            result.put(
                                    name,
                                    value instanceof java.math.BigDecimal decimal
                                            ? decimal.toPlainString()
                                            : value instanceof Number ? value.toString() : value));
        return Collections.unmodifiableMap(result);
    }

    /**
     * PageData，负责当前模块对应的契约或处理职责。
     *
     * @param columns 输出列契约
     * @param rows 精确类型的数据行
     * @param total 当前契约的total配置或状态
     * @param dataTime 当前契约的dataTime配置或状态
     * @param mode 当前契约的mode配置或状态
     * @param summaries 当前契约的summaries配置或状态
     */
    public record PageData(
            List<ReportDsl.Column> columns,
            List<Map<String, TypedValue>> rows,
            long total,
            String dataTime,
            String mode,
            List<ReportCalculator.SummaryValue> summaries) {}

    /**
     * 访问，负责当前模块对应的契约或处理职责。
     *
     * @param actor 执行用户
     * @param run 当前契约的run配置或状态
     * @param version 当前契约的version配置或状态
     * @param binding 当前契约的binding配置或状态
     * @param payload 当前契约的payload配置或状态
     * @param lease 当前尝试的租约截止时刻
     * @param permission 当前契约的permission配置或状态
     * @param action 受控资源操作
     */
    public record Access(
            ReportActor actor,
            Run run,
            Version version,
            Binding binding,
            Payload payload,
            ExportJob lease,
            String permission,
            String action) {
        /**
         * 初始化访问并保存所需依赖或不可变值。
         *
         * @param actor 当前服务端身份
         * @param run 当前报表运行
         * @param version 固定版本
         * @param binding 当前操作的 {@code binding} 输入
         * @param payload 当前操作的 {@code payload} 输入
         */
        public Access(
                ReportActor actor, Run run, Version version, Binding binding, Payload payload) {
            this(actor, run, version, binding, payload, null, EXPORT, "EXPORT");
        }

        /**
         * 初始化访问并保存所需依赖或不可变值。
         *
         * @param actor 当前服务端身份
         * @param run 当前报表运行
         * @param version 固定版本
         * @param binding 当前操作的 {@code binding} 输入
         * @param payload 当前操作的 {@code payload} 输入
         * @param lease 已领取的任务围栏
         */
        public Access(
                ReportActor actor,
                Run run,
                Version version,
                Binding binding,
                Payload payload,
                ExportJob lease) {
            this(actor, run, version, binding, payload, lease, EXPORT, "EXPORT");
        }
    }

    /**
     * 完成 {@code payload} 对应的报表运行服务处理。
     *
     * @param run 当前报表运行
     * @return 当前处理返回的固定结果
     */
    public Payload payload(Run run) {
        try {
            return Objects.requireNonNull(json.readValue(run.parameters(), Payload.class));
        } catch (Exception invalid) {
            throw new ReportFault("SNAPSHOT_INVALID", "报表运行配置无法解析", 500);
        }
    }

    /**
     * 完成 {@code json} 对应的报表运行服务处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception invalid) {
            throw new ReportFault("SERIALIZATION_FAILED", "运行配置无法序列化", 500);
        }
    }

    /**
     * 完成 {@code spreadsheetExporter} 对应的报表运行服务处理。
     *
     * @param previous 当前操作的 {@code previous} 输入
     * @param budget 当前操作的 {@code budget} 输入
     * @return 当前处理返回的固定结果
     */
    public com.nexus.report.export.ReportSpreadsheetExporter spreadsheetExporter(
            Access previous, long budget) {
        var access = recheck(previous);
        return new com.nexus.report.export.ReportSpreadsheetExporter(
                artifacts, access.actor().tenantId(), budget);
    }

    /**
     * 列出业务场景可运行的已发布模板。
     *
     * @param actor 当前服务端身份
     * @param code 业务编码
     * @return 当前处理返回的固定结果
     */
    public java.util.List<ReportDefinitionService.BusinessTemplate> businessTemplates(
            ReportActor actor, String code) {
        return definitions.businessTemplates(actor, code);
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param actor 当前服务端身份
     * @param report 已编译报表
     * @param requestedMode 当前操作的 {@code requestedMode} 输入
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    public Run create(
            ReportActor actor, String report, String requestedMode, Map<String, Object> input) {
        return create(actor, report, requestedMode, input, null);
    }

    /**
     * 当前身份和访问权限验证后核对预期发布版本，再绑定已解析的不可变版本执行。
     *
     * @param actor 当前服务端身份
     * @param report 报表标识
     * @param requestedMode 运行一致性模式
     * @param input 待校验输入
     * @param expectedVersionId 调用方确认的发布版本，空值保留旧调用语义
     * @return 绑定固定发布版本的运行
     */
    public Run create(
            ReportActor actor,
            String report,
            String requestedMode,
            Map<String, Object> input,
            String expectedVersionId) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        Version version = definitions.published(current, report, "VIEW", PREVIEW);
        if (expectedVersionId != null && expectedVersionId.isBlank())
            throw ReportFault.invalid("预期发布版本不能为空");
        if (expectedVersionId != null && !expectedVersionId.equals(version.id()))
            throw new ReportFault("REPORT_VERSION_CHANGED", "模板发布版本已变化，请重新选择模板", 409);
        return createBound(current, version, requestedMode, input);
    }

    /**
     * 以明确的不可变发布版本建立运行，供批量单据和订阅调用。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @param mode 当前契约的mode配置或状态
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    public Run createFixed(
            ReportActor actor, String versionId, String mode, Map<String, Object> input) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var version =
                store.version(current.tenantId(), versionId)
                        .orElseThrow(ReportRuntimeService::missing);
        var definition = definitions.definition(current, version.reportId(), "VIEW", PREVIEW);
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "报表已下线", 409);
        definitions.bindVersion(current, version);
        return createBound(current, version, mode, input);
    }

    /**
     * 在当前身份下固定版本、范围和规范化参数后生成运行。
     *
     * @param current 当前契约的current配置或状态
     * @param version 当前契约的version配置或状态
     * @param requestedMode 当前契约的requestedMode配置或状态
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    private Run createBound(
            ReportActor current, Version version, String requestedMode, Map<String, Object> input) {
        Binding binding = definitions.bindVersion(current, version);
        String mode =
                binding.compiled().dsl().kind() != ReportDsl.Kind.TABLE
                        ? "SNAPSHOT"
                        : requestedMode;
        if (mode == null || !Set.of("LIVE", "SNAPSHOT").contains(mode))
            throw new ReportFault("INVALID_PARAMETER", "运行模式无效");
        if (mode.equals("LIVE")
                && (binding.compiled().aggregateExpressions()
                        || binding.compiled().dsl().summaries().stream()
                                .anyMatch(
                                        summary ->
                                                !summary.function().equals("COUNT_ROWS")
                                                        || !summary.groupBy().isEmpty()))) {
            throw new ReportFault("SNAPSHOT_REQUIRED", "聚合公式或分组汇总需要快照运行");
        }
        Map<String, Object> parameters = definitions.parameters(binding, input);
        String scope = definitions.fingerprint(current, binding);
        Payload payload = new Payload(parameters, artifacts.storeId(), null, List.of());
        if (mode.equals("SNAPSHOT")) {
            var probe =
                    binding.provider()
                            .query(
                                    binding.compiled().context(current),
                                    new DatasetQuery(
                                            parameters,
                                            binding.compiled().requiredFields(),
                                            1,
                                            1,
                                            false,
                                            null,
                                            false));
            if (probe.total() > 5000)
                throw new ReportFault("CAPACITY_EXCEEDED", "交互快照最多五千行；大型列表请使用实时分页和明细导出");
        }
        current = definitions.resolve(current.tenantId(), current.userId());
        definitions.definition(current, version.reportId(), "VIEW", PREVIEW);
        binding = definitions.bindVersion(current, version);
        if (!scope.equals(definitions.fingerprint(current, binding))) throw scopeChanged();
        Run run =
                store.createRun(
                        current,
                        version.reportId(),
                        version.id(),
                        mode,
                        json(payload),
                        scope,
                        Instant.now().plusSeconds(properties.getRetentionHours() * 3600L));
        if (mode.equals("LIVE")) {
            if (!store.transitionRun(
                            current.tenantId(),
                            run.id(),
                            "RUNNING",
                            null,
                            null,
                            0,
                            json(payload),
                            null)
                    || !store.transitionRun(
                            current.tenantId(),
                            run.id(),
                            "SUCCEEDED",
                            null,
                            null,
                            0,
                            json(payload),
                            null)) throw buildConflict();
        } else {
            buildSnapshot(
                    new Access(current, run, version, binding, payload, null, PREVIEW, "VIEW"),
                    new ReportCancellation(),
                    5000,
                    PREVIEW,
                    "VIEW");
        }
        store.audit(current, version.reportId(), "RUN", "SUCCEEDED", null);
        return store.run(current.tenantId(), run.id()).orElseThrow(ReportRuntimeService::missing);
    }

    /**
     * 完成 {@code access} 对应的报表运行服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param permission 待核对权限
     * @param action 资源操作
     * @return 当前处理返回的固定结果
     */
    public Access access(ReportActor actor, String id, String permission, String action) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        purpose(permission, action);
        Run run = store.run(current.tenantId(), id).orElseThrow(ReportRuntimeService::missing);
        if (run.actor() != current.userId()) throw ReportFault.denied();
        if (!run.expires().isAfter(Instant.now()) || run.state().equals("EXPIRED")) throw expired();
        Definition definition = definitions.definition(current, run.reportId(), action, permission);
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "报表已经下线", 409);
        Version version =
                store.version(current.tenantId(), run.version())
                        .orElseThrow(ReportRuntimeService::missing);
        if (!version.reportId().equals(run.reportId())) throw missing();
        Binding binding = definitions.bindVersion(current, version);
        if ("EXPORT".equals(action)) definitions.requireDatasetExport(current, binding);
        if (!Objects.equals(run.scopeHash(), definitions.fingerprint(current, binding)))
            throw scopeChanged();
        Payload payload = payload(run);
        if (payload.constraint() != null) constraintGuard.accept(current, payload.constraint());
        if (!artifacts.storeId().equals(payload.store()))
            throw new ReportFault("STORAGE_NODE_MISMATCH", "当前存储节点没有该快照，请在原节点访问或启用共享存储", 409);
        return new Access(current, run, version, binding, payload, null, permission, action);
    }

    /**
     * 完成 {@code purpose} 对应的报表运行服务处理。
     *
     * @param permission 待核对权限
     * @param action 资源操作
     */
    private static void purpose(String permission, String action) {
        if (!(PREVIEW.equals(permission) && "VIEW".equals(action)
                || EXPORT.equals(permission) && "EXPORT".equals(action)))
            throw ReportFault.denied();
    }

    /**
     * 完成 {@code recheck} 对应的报表运行服务处理。
     *
     * @param previous 当前操作的 {@code previous} 输入
     * @return 当前处理返回的固定结果
     */
    private Access recheck(Access previous) {
        if (previous.lease() != null && !store.leaseValid(previous.lease())) throw leaseLost();
        Access current =
                access(
                        previous.actor(),
                        previous.run().id(),
                        previous.permission(),
                        previous.action());
        if (previous.lease() != null && !store.leaseValid(previous.lease())) throw leaseLost();
        return new Access(
                current.actor(),
                current.run(),
                current.version(),
                current.binding(),
                current.payload(),
                previous.lease(),
                previous.permission(),
                previous.action());
    }

    /**
     * 重新鉴权后读取运行状态。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    public Run status(ReportActor actor, String id) {
        return access(actor, id, PREVIEW, "VIEW").run();
    }

    /**
     * 读取当前授权范围内的精确分页结果。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param page 当前页
     * @param size 有界页大小
     * @param sort 当前操作的 {@code sort} 输入
     * @param descending 当前操作的 {@code descending} 输入
     * @return 当前处理返回的固定结果
     */
    /**
     * 读取固定快照交叉结果，读取前后均检查权限指纹。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public com.nexus.report.pivot.AggregationExecutor.Result pivot(ReportActor actor, String id) {
        var access = access(actor, id, PREVIEW, "VIEW");
        var result = pivotResult(access);
        recheck(access);
        return result;
    }

    /**
     * 钻取只接受同一快照计算的坐标，绝不再次查询实时业务库。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param coordinate 本次固定快照的服务端钻取坐标
     * @param offset 当前契约的offset配置或状态
     * @param size 当前契约的size配置或状态
     * @return 当前受控处理的固定结果
     */
    public com.nexus.report.pivot.AggregationExecutor.Drill drill(
            ReportActor actor, String id, String coordinate, int offset, int size) {
        var access = access(actor, id, PREVIEW, "VIEW");
        var rows = pivotRows(access);
        var result =
                new com.nexus.report.pivot.AggregationExecutor()
                        .drill(
                                access.binding().compiled().dsl().pivot(),
                                access.binding().compiled().descriptor(),
                                rows,
                                coordinate,
                                offset,
                                size);
        recheck(access);
        return result;
    }

    /**
     * 数据XLSX使用同一快照聚合投影。
     *
     * @param previous 当前契约的previous配置或状态
     * @return 当前受控处理的固定结果
     */
    public com.nexus.report.pivot.PivotProjection.Projection pivotProjection(Access previous) {
        var access = recheck(previous);
        var result =
                new com.nexus.report.pivot.PivotProjection()
                        .project(access.binding().compiled().dsl(), pivotResult(access));
        recheck(access);
        return result;
    }

    /**
     * 聚合固定原始快照，结果不进入跨用户缓存。
     *
     * @param access 当前契约的access配置或状态
     * @return 当前受控处理的固定结果
     */
    private com.nexus.report.pivot.AggregationExecutor.Result pivotResult(Access access) {
        return new com.nexus.report.pivot.AggregationExecutor()
                .aggregate(
                        access.binding().compiled().dsl().pivot(),
                        access.binding().compiled().descriptor(),
                        pivotRows(access));
    }

    /**
     * 验证运行、类型、存储完整性和当前资源权限。
     *
     * @param previous 当前契约的previous配置或状态
     * @return 当前受控处理的固定结果
     */
    private List<Map<String, TypedValue>> pivotRows(Access previous) {
        var access = recheck(previous);
        ready(access.run());
        if (access.binding().compiled().dsl().kind() != ReportDsl.Kind.PIVOT
                || !access.run().mode().equals("SNAPSHOT")) throw ReportFault.invalid("运行不是固定交叉快照");
        if (access.run().rows() > 5000) throw ReportFault.capacity();
        snapshots.verify(
                access.run().tenant(), access.run().snapshot(), access.payload().checksum());
        var rows = snapshots.page(access.run().tenant(), access.run().snapshot(), 0, 5000);
        recheck(access);
        return rows;
    }

    /**
     * 执行page对应的受控处理。
     *
     * @param actor 当前受控操作的actor输入
     * @param id 当前受控操作的id输入
     * @param page 当前受控操作的page输入
     * @param size 当前受控操作的size输入
     * @param sort 当前受控操作的sort输入
     * @param descending 当前受控操作的descending输入
     * @return 当前固定契约或处理结果
     */
    public PageData page(
            ReportActor actor, String id, int page, int size, String sort, boolean descending) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 200)
            throw new ReportFault("INVALID_PARAMETER", "分页范围无效");
        Access access = access(actor, id, PREVIEW, "VIEW");
        Run run = access.run();
        Binding binding = access.binding();
        ready(run);
        PageData result;
        if (binding.compiled().dsl().kind() == ReportDsl.Kind.PIVOT) {
            if (sort != null) throw ReportFault.invalid("交叉结果顺序已经固定");
            var projection = pivotProjection(access);
            var resultRows = projection.data().rows();
            result =
                    new PageData(
                            projection.dsl().columns(),
                            resultRows.stream().skip((long) (page - 1) * size).limit(size).toList(),
                            resultRows.size(),
                            run.dataTime().toString(),
                            "SNAPSHOT",
                            projection.data().summaries());
        } else if (run.mode().equals("SNAPSHOT")) {
            if (sort != null) throw new ReportFault("INVALID_PARAMETER", "快照顺序已经固定，排序请建立新运行");
            snapshots.verify(run.tenant(), run.snapshot(), access.payload().checksum());
            recheck(access);
            var rows = snapshots.page(run.tenant(), run.snapshot(), (long) (page - 1) * size, size);
            result =
                    new PageData(
                            binding.compiled().dsl().columns(),
                            ReportDefinitionService.project(rows, binding.compiled().dsl()),
                            run.rows(),
                            run.dataTime().toString(),
                            "SNAPSHOT",
                            access.payload().summaries());
        } else {
            if (sort != null
                    && binding.compiled().dsl().columns().stream()
                            .noneMatch(
                                    column ->
                                            column.field().equals(sort)
                                                    && !binding.compiled()
                                                            .expressions()
                                                            .containsKey(sort))) {
                throw new ReportFault("INVALID_PARAMETER", "只能按当前可见的可信字段排序");
            }
            var source =
                    metrics.measure(
                            ReportRuntimeMetrics.Phase.QUERY,
                            () ->
                                    binding.provider()
                                            .query(
                                                    binding.compiled().context(access.actor()),
                                                    new DatasetQuery(
                                                            access.payload().input(),
                                                            binding.compiled().requiredFields(),
                                                            page,
                                                            size,
                                                            false,
                                                            sort,
                                                            descending)));
            if (source.rows().size() > size) throw ReportFault.capacity();
            var calculated =
                    metrics.measure(
                            ReportRuntimeMetrics.Phase.CALCULATION,
                            () ->
                                    binding.engine()
                                            .calculate(
                                                    binding.compiled(),
                                                    source.rows(),
                                                    access.payload().input()));
            var summaries =
                    binding.compiled().dsl().summaries().stream()
                            .map(
                                    summary ->
                                            new ReportCalculator.SummaryValue(
                                                    summary.label(),
                                                    Map.of(),
                                                    TypedValue.of(
                                                            ValueType.INTEGER, source.total())))
                            .toList();
            result =
                    new PageData(
                            binding.compiled().dsl().columns(),
                            ReportDefinitionService.project(
                                    calculated.rows(), binding.compiled().dsl()),
                            source.total(),
                            source.dataTime().toString(),
                            "LIVE",
                            summaries);
        }
        recheck(access);
        return result;
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param permission 待核对权限
     * @param action 资源操作
     * @return 当前处理返回的固定结果
     */
    public RenderPlan layout(ReportActor actor, String id, String permission, String action) {
        return layout(access(actor, id, permission, action));
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param previous 当前操作的 {@code previous} 输入
     * @return 当前处理返回的固定结果
     */
    public RenderPlan layout(Access previous) {
        return layout(previous, new ReportCancellation());
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param previous 当前操作的 {@code previous} 输入
     * @param cancellation 执行取消信号
     * @return 当前处理返回的固定结果
     */
    public RenderPlan layout(Access previous, ReportCancellation cancellation) {
        cancellation.check();
        Access access = recheck(previous);
        Run run = access.run();
        ready(run);
        if (!run.mode().equals("SNAPSHOT"))
            throw new ReportFault("SNAPSHOT_REQUIRED", "文档排版需要完成的快照运行");
        if (run.rows() > 5000) throw ReportFault.capacity();
        snapshots.verify(run.tenant(), run.snapshot(), access.payload().checksum());
        access = recheck(access);
        cancellation.check();
        ReportRenderPlanStore.Artifact candidate = null;
        try {
            if (access.run().plan() == null) {
                var rows = snapshots.page(run.tenant(), run.snapshot(), 0, 5000);
                var data = new ReportCalculator.CalculatedData(rows, access.payload().summaries());
                Access measured = access;
                candidate =
                        plans.create(
                                run.tenant(),
                                run.id(),
                                access.payload().checksum(),
                                access.version().engine(),
                                FontSupport.find(properties.getFontPath()),
                                cancellation,
                                font ->
                                        metrics.measure(
                                                ReportRuntimeMetrics.Phase.LAYOUT,
                                                () ->
                                                        measured.binding()
                                                                .engine()
                                                                .layout(
                                                                        measured.binding()
                                                                                .compiled(),
                                                                        data,
                                                                        measured.payload().input(),
                                                                        font)));
                cancellation.check();
                access = recheck(access);
                Payload payload =
                        new Payload(
                                access.payload().input(),
                                access.payload().store(),
                                access.payload().checksum(),
                                access.payload().summaries(),
                                candidate.checksum(),
                                access.payload().constraint());
                boolean registered =
                        store.publishRenderPlan(
                                run.tenant(),
                                run.id(),
                                run.snapshot(),
                                candidate.reference(),
                                json(payload),
                                access.lease());
                access = recheck(access);
                if (!registered && access.run().plan() == null) throw buildConflict();
            }
            RenderPlan plan =
                    plans.read(
                            run.tenant(),
                            access.run().plan(),
                            access.payload().planChecksum(),
                            run.id(),
                            access.payload().checksum(),
                            access.version().engine(),
                            run.rows(),
                            cancellation);
            cancellation.check();
            recheck(access);
            return plan;
        } finally {
            if (candidate != null) {
                Run current = store.run(run.tenant(), run.id()).orElse(null);
                if (current == null || !candidate.reference().equals(current.plan()))
                    artifacts.delete(run.tenant(), candidate.reference());
            }
        }
    }

    /**
     * 仅服务端导出器接收固定字体的私有路径，接口不暴露物理路径。
     *
     * @param previous 当前契约的previous配置或状态
     * @param cancellation 当前契约的cancellation配置或状态
     * @param action 受控资源操作
     */
    public void withLayoutFont(
            Access previous,
            ReportCancellation cancellation,
            java.util.function.Consumer<java.nio.file.Path> action) {
        cancellation.check();
        Access access = recheck(previous);
        ready(access.run());
        if (access.run().plan() == null) throw new ReportFault("PLAN_NOT_READY", "排版产物尚未生成", 409);
        snapshots.verify(
                access.run().tenant(), access.run().snapshot(), access.payload().checksum());
        recheck(access);
        plans.withFont(
                access.run().tenant(),
                access.run().plan(),
                access.payload().planChecksum(),
                access.run().id(),
                access.payload().checksum(),
                access.version().engine(),
                access.run().rows(),
                cancellation,
                font -> {
                    cancellation.check();
                    recheck(access);
                    action.accept(font);
                    cancellation.check();
                    recheck(access);
                });
        cancellation.check();
        recheck(access);
    }

    /**
     * 按幂等键和租户配额创建导出任务。
     *
     * @param actor 当前服务端身份
     * @param origin 当前操作的 {@code origin} 输入
     * @param format 当前操作的 {@code format} 输入
     * @param requestKey 当前操作的 {@code requestKey} 输入
     * @return 当前处理返回的固定结果
     */
    public ExportJob enqueue(ReportActor actor, String origin, String format, String requestKey) {
        if (format == null
                || !Set.of("PDF", "XLSX_DATA", "XLSX_LAYOUT").contains(format)
                || requestKey == null
                || !requestKey.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new ReportFault("INVALID_PARAMETER", "导出类型或请求标识无效");
        }
        Access access = access(actor, origin, EXPORT, "EXPORT");
        ready(access.run());
        String fingerprint = ReportDefinitionService.hash(access.run().id() + ":" + format);
        var prior = store.jobByRequest(access.actor(), requestKey);
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(fingerprint)
                    || !prior.get().format().equals(format))
                throw new ReportFault("REVISION_CONFLICT", "同一请求标识不能更改运行或格式", 409);
            return prior.get();
        }
        recheck(access);
        ExportJob job =
                store.createExportJob(
                        access.actor(),
                        access.run().id(),
                        format,
                        requestKey,
                        fingerprint,
                        access.run().expires());
        store.audit(access.actor(), access.run().reportId(), "EXPORT_ENQUEUE", "SUCCEEDED", null);
        return job;
    }

    /**
     * 完成 {@code job} 对应的报表运行服务处理。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    /**
     * 固定版本导出直接进入持久任务队列，每个接收人独立身份和权限指纹。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @param format 受控格式
     * @param input 已规范化的参数值
     * @param requestKey 当前契约的requestKey配置或状态
     * @return 当前受控处理的固定结果
     */
    public ExportJob enqueueFixed(
            ReportActor actor,
            String versionId,
            String format,
            Map<String, Object> input,
            String requestKey) {
        return enqueueFixed(actor, versionId, format, input, requestKey, null);
    }

    /**
     * 服务端订阅固定资源来源，客户端普通导出不能添加此约束。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @param format 受控格式
     * @param input 已规范化的参数值
     * @param requestKey 当前契约的requestKey配置或状态
     * @param source 当前契约的source配置或状态
     * @return 当前受控处理的固定结果
     */
    public ExportJob enqueueFixed(
            ReportActor actor,
            String versionId,
            String format,
            Map<String, Object> input,
            String requestKey,
            SourceConstraint source) {
        if (format == null
                || !Set.of("PDF", "XLSX_DATA", "XLSX_LAYOUT").contains(format)
                || requestKey == null
                || !requestKey.matches("[A-Za-z0-9_-]{1,80}"))
            throw ReportFault.invalid("固定导出参数无效");
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, PREVIEW);
        ReportDefinitionService.require(current, EXPORT);
        var version =
                store.version(current.tenantId(), versionId)
                        .orElseThrow(ReportRuntimeService::missing);
        var definition = definitions.definition(current, version.reportId(), "EXPORT", EXPORT);
        definitions.definition(current, version.reportId(), "VIEW", PREVIEW);
        if (!definition.status().equals("PUBLISHED"))
            throw new ReportFault("REPORT_OFFLINE", "固定报表已下线", 409);
        var binding = definitions.bindVersion(current, version);
        definitions.requireDatasetExport(current, binding);
        var parameters =
                source == null
                        ? definitions.parameters(binding, input)
                        : com.nexus.common.report.DatasetQueryValidator.parameters(
                                binding.compiled().descriptor(), input);
        String scope = definitions.fingerprint(current, binding);
        if (source != null) constraintGuard.accept(current, source);
        var payload = new Payload(parameters, artifacts.storeId(), null, List.of(), null, source);
        String hash =
                ReportDefinitionService.hash(
                        "FIXED:"
                                + version.id()
                                + ":"
                                + format
                                + ":"
                                + json(new TreeMap<>(payload.input()))
                                + ":"
                                + scope
                                + ":"
                                + json(source));
        var prior = store.jobByRequest(current, requestKey);
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(hash) || !prior.get().format().equals(format))
                throw new ReportFault("REVISION_CONFLICT", "固定导出幂等参数或权限范围已变化", 409);
            return job(current, prior.get().id());
        }
        current = definitions.resolve(current.tenantId(), current.userId());
        definitions.definition(current, version.reportId(), "EXPORT", EXPORT);
        if (!scope.equals(
                definitions.fingerprint(current, definitions.bindVersion(current, version))))
            throw scopeChanged();
        var queued =
                store.createFixedExport(
                        current,
                        version,
                        json(payload),
                        scope,
                        format,
                        requestKey,
                        hash,
                        Instant.now().plusSeconds(properties.getRetentionHours() * 3600L));
        access(current, queued.runId(), EXPORT, "EXPORT");
        store.audit(current, version.reportId(), "FIXED_EXPORT_ENQUEUE", "SUCCEEDED", null);
        return queued;
    }

    /**
     * 执行job对应的受控处理。
     *
     * @param actor 当前受控操作的actor输入
     * @param id 当前受控操作的id输入
     * @return 当前固定契约或处理结果
     */
    public ExportJob job(ReportActor actor, String id) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, EXPORT);
        ExportJob job =
                store.job(current.tenantId(), id).orElseThrow(ReportRuntimeService::missing);
        if (job.actor() != current.userId()) throw ReportFault.denied();
        access(current, job.runId(), EXPORT, "EXPORT");
        return job;
    }

    /**
     * 完成 {@code jobs} 对应的报表运行服务处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public List<ExportJob> jobs(ReportActor actor) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, EXPORT);
        /** 列表只返回仍有权限的资源，执行及下载继续进行完整范围检查。 */
        return store.jobs(current).stream()
                .filter(
                        job -> {
                            try {
                                access(current, job.runId(), EXPORT, "EXPORT");
                                return true;
                            } catch (ReportFault denied) {
                                return false;
                            }
                        })
                .toList();
    }

    /**
     * 重新鉴权后请求原任务重试。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     */
    public void retry(ReportActor actor, String id) {
        retry(actor, id, job(actor, id).retryRound());
    }

    /**
     * 重新鉴权后请求原任务重试。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     * @param expectedRound 预期重试轮次
     */
    public void retry(ReportActor actor, String id, int expectedRound) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        var job = job(current, id);
        access(current, job.runId(), EXPORT, "EXPORT");
        if (!store.manualRetry(current, id, expectedRound))
            throw new ReportFault("RETRY_NOT_ALLOWED", "任务不可重试或已处理", 409);
        store.audit(current, null, "EXPORT_RETRY", "REQUESTED", null);
    }

    /**
     * 完成 {@code progress} 对应的报表运行服务处理。
     *
     * @param access 当前操作的 {@code access} 输入
     * @param phase 当前操作的 {@code phase} 输入
     * @param rows 待处理业务行
     * @param total 当前操作的 {@code total} 输入
     * @param cancellation 执行取消信号
     */
    public void progress(
            Access access, String phase, long rows, Long total, ReportCancellation cancellation) {
        cancellation.check();
        if (access.lease() != null && !store.progressJob(access.lease(), phase, rows, total)) {
            cancellation.cancel("LEASE_LOST");
            throw leaseLost();
        }
    }

    /**
     * 请求取消当前用户拥有的任务。
     *
     * @param actor 当前服务端身份
     * @param id 资源标识
     */
    public void cancel(ReportActor actor, String id) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, EXPORT);
        ExportJob job =
                store.job(current.tenantId(), id).orElseThrow(ReportRuntimeService::missing);
        if (job.actor() != current.userId()) throw ReportFault.denied();
        store.cancel(current, id);
        store.audit(current, null, "EXPORT_CANCEL", "REQUESTED", null);
    }

    /**
     * 完成 {@code execution} 对应的报表运行服务处理。
     *
     * @param claimed 当前操作的 {@code claimed} 输入
     * @return 当前处理返回的固定结果
     */
    public Access execution(ExportJob claimed) {
        if (!claimed.expires().isAfter(Instant.now())) throw expired();
        if (!store.leaseValid(claimed)) throw leaseLost();
        ReportActor current = definitions.resolve(claimed.tenant(), claimed.actor());
        Access access = access(current, claimed.runId(), EXPORT, "EXPORT");
        if (!store.leaseValid(claimed)) throw leaseLost();
        return new Access(
                access.actor(),
                access.run(),
                access.version(),
                access.binding(),
                access.payload(),
                claimed,
                EXPORT,
                "EXPORT");
    }

    /**
     * 兼容工作线程的默认快照构建入口，导出快照始终要求导出授权。
     *
     * @param access 当前契约的access配置或状态
     * @param cancellation 当前契约的cancellation配置或状态
     * @param maximum 当前契约的maximum配置或状态
     * @return 当前受控处理的固定结果
     */
    public Access buildSnapshot(Access access, ReportCancellation cancellation, int maximum) {
        return buildSnapshot(access, cancellation, maximum, EXPORT, "EXPORT");
    }

    /**
     * 按当前权限和固定发布建立私有快照。
     *
     * @param previous 当前操作的 {@code previous} 输入
     * @param cancellation 执行取消信号
     * @param maximum 当前操作的 {@code maximum} 输入
     * @param permission 待核对权限
     * @param action 资源操作
     * @return 当前处理返回的固定结果
     */
    public Access buildSnapshot(
            Access previous,
            ReportCancellation cancellation,
            int maximum,
            String permission,
            String action) {
        purpose(permission, action);
        cancellation.check();
        Access purposeAccess =
                new Access(
                        previous.actor(),
                        previous.run(),
                        previous.version(),
                        previous.binding(),
                        previous.payload(),
                        previous.lease(),
                        permission,
                        action);
        Access access = recheck(purposeAccess);
        Run run = access.run();
        if (run.state().equals("SUCCEEDED") && run.snapshot() != null) {
            snapshots.verify(run.tenant(), run.snapshot(), access.payload().checksum());
            cancellation.check();
            return recheck(access);
        }
        if (EXPORT.equals(permission) && access.lease() == null) throw leaseLost();
        String checkpoint =
                access.lease() == null
                        ? "build/preview/" + UUID.randomUUID()
                        : "build/job/"
                                + access.lease().id()
                                + "/"
                                + access.lease().attempts()
                                + "/"
                                + UUID.randomUUID();
        if (!store.beginSnapshot(run.tenant(), run.id(), checkpoint, access.lease()))
            throw buildConflict();
        ReportSnapshotStore.Snapshot snapshot = null;
        try {
            cancellation.check();
            access = recheck(access);
            Access building = access;
            snapshot =
                    metrics.measure(
                            ReportRuntimeMetrics.Phase.SNAPSHOT,
                            () ->
                                    snapshots.create(
                                            building.binding().compiled(),
                                            building.binding().provider(),
                                            building.actor(),
                                            building.payload().input(),
                                            cancellation,
                                            maximum,
                                            building.binding().engine(),
                                            rows ->
                                                    progress(
                                                            building,
                                                            "SNAPSHOT",
                                                            rows,
                                                            null,
                                                            cancellation)));
            cancellation.check();
            access = recheck(access);
            cancellation.check();
            Payload payload =
                    new Payload(
                            access.payload().input(),
                            artifacts.storeId(),
                            snapshot.checksum(),
                            snapshot.summaries(),
                            null,
                            access.payload().constraint());
            if (!store.finishSnapshot(
                    run.tenant(),
                    run.id(),
                    checkpoint,
                    access.lease(),
                    "SUCCEEDED",
                    snapshot.reference(),
                    snapshot.rows(),
                    json(payload),
                    null)) throw leaseLost();
            return recheck(
                    new Access(
                            access.actor(),
                            store.run(run.tenant(), run.id())
                                    .orElseThrow(ReportRuntimeService::missing),
                            access.version(),
                            access.binding(),
                            payload,
                            access.lease(),
                            permission,
                            action));
        } catch (RuntimeException failure) {
            if (snapshot != null) {
                Run current = store.run(run.tenant(), run.id()).orElse(null);
                /** 规范快照成功后予以保留，即使后续交付授权被撤销也不覆盖已有运行。 */
                if (current == null || !snapshot.reference().equals(current.snapshot()))
                    artifacts.delete(run.tenant(), snapshot.reference());
            }
            String error = code(failure);
            String state =
                    access.lease() != null && retryable(error)
                            ? "QUEUED"
                            : error.equals("CANCELLED") ? "CANCELLED" : "FAILED";
            store.finishSnapshot(
                    run.tenant(),
                    run.id(),
                    checkpoint,
                    access.lease(),
                    state,
                    null,
                    0,
                    run.parameters(),
                    error);
            throw failure;
        }
    }

    /**
     * 完成 {@code each} 对应的报表运行服务处理。
     *
     * @param previous 当前操作的 {@code previous} 输入
     * @param consumer 当前操作的 {@code consumer} 输入
     * @param cancellation 执行取消信号
     */
    public void each(
            Access previous,
            java.util.function.Consumer<Map<String, TypedValue>> consumer,
            ReportCancellation cancellation) {
        cancellation.check();
        Access access = recheck(previous);
        ready(access.run());
        if (!access.run().mode().equals("SNAPSHOT"))
            throw new ReportFault("SNAPSHOT_REQUIRED", "导出需要固定快照");
        snapshots.verify(
                access.run().tenant(), access.run().snapshot(), access.payload().checksum());
        cancellation.check();
        recheck(access);
        long[] rows = {0};
        snapshots.each(
                access.run().tenant(),
                access.run().snapshot(),
                row -> {
                    cancellation.check();
                    if (rows[0]++ % 128 == 0) recheck(access);
                    consumer.accept(row);
                    if (rows[0] % 128 == 0)
                        progress(access, "WRITING", rows[0], access.run().rows(), cancellation);
                },
                cancellation);
        cancellation.check();
        recheck(access);
    }

    /**
     * 完成 {@code ready} 对应的报表运行服务处理。
     *
     * @param run 当前报表运行
     */
    private static void ready(Run run) {
        if (!run.state().equals("SUCCEEDED"))
            throw new ReportFault("RUN_NOT_READY", "报表运行尚未完成", 409);
    }

    /**
     * 完成 {@code retryable} 对应的报表运行服务处理。
     *
     * @param code 业务编码
     * @return 当前检查或条件操作是否成功
     */
    private static boolean retryable(String code) {
        return Set.of(
                        "STORAGE_UNAVAILABLE",
                        "ARTIFACT_STORE_UNAVAILABLE",
                        "XLSX_EXPORT_FAILED",
                        "PDF_EXPORT_FAILED",
                        "REPORT_EXECUTION_FAILED")
                .contains(code);
    }

    /**
     * 完成 {@code code} 对应的报表运行服务处理。
     *
     * @param failure 当前操作的 {@code failure} 输入
     * @return 当前处理返回的固定结果
     */
    public static String code(Throwable failure) {
        String code =
                failure instanceof ReportFault fault
                        ? fault.code()
                        : failure instanceof DatasetAccessException fault
                                ? fault.code()
                                : "REPORT_EXECUTION_FAILED";
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,63}")
                ? code
                : "REPORT_EXECUTION_FAILED";
    }

    /**
     * 完成 {@code expired} 对应的报表运行服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault expired() {
        return new ReportFault("SNAPSHOT_EXPIRED", "报表运行已经过期", 410);
    }

    /**
     * 完成 {@code scopeChanged} 对应的报表运行服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault scopeChanged() {
        return new ReportFault("SCOPE_CHANGED", "权限范围已变化，请重新运行报表", 403);
    }

    /**
     * 完成 {@code leaseLost} 对应的报表运行服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault leaseLost() {
        return new ReportFault("LEASE_LOST", "导出任务租约已变化或执行已停止", 409);
    }

    /**
     * 完成 {@code buildConflict} 对应的报表运行服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault buildConflict() {
        return new ReportFault("SNAPSHOT_BUILD_IN_PROGRESS", "快照正在构建或状态已经变化", 409);
    }

    /**
     * 完成 {@code missing} 对应的报表运行服务处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault missing() {
        return new ReportFault("NOT_FOUND", "报表运行不存在或不可访问", 404);
    }
}
