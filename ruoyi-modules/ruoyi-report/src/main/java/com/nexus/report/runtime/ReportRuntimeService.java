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

/** Every delivery revalidates its fixed version, inputs, data scope and any acquired worker lease. */
public class ReportRuntimeService {
    private static final String PREVIEW = "report:runtime:preview";
    private static final String EXPORT = "report:runtime:export";
    private final JdbcReportStore store;
    private final ReportDefinitionService definitions;
    private final ReportCompiler compiler;
    private final ReportSnapshotStore snapshots;
    private final ReportRenderPlanStore plans;
    private final ReportArtifactStore artifacts;
    private final ReportProperties properties;
    private final ObjectMapper json;

    public ReportRuntimeService(JdbcReportStore store, ReportDefinitionService definitions, ReportCompiler compiler,
                                ReportSnapshotStore snapshots, ReportArtifactStore artifacts, ReportProperties properties, ObjectMapper json) {
        this.store = store; this.definitions = definitions; this.compiler = compiler; this.snapshots = snapshots;
        this.artifacts = artifacts; this.properties = properties; this.plans = new ReportRenderPlanStore(artifacts, json);
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
    public record Payload(Map<String, Object> input, String store, String checksum, List<ReportCalculator.SummaryValue> summaries, String planChecksum) {
        public Payload(Map<String, Object> input, String store, String checksum, List<ReportCalculator.SummaryValue> summaries) { this(input, store, checksum, summaries, null); }
        public Payload { input = Collections.unmodifiableMap(new LinkedHashMap<>(input == null ? Map.of() : input)); summaries = List.copyOf(summaries == null ? List.of() : summaries); }
    }
    public record PageData(List<ReportDsl.Column> columns, List<Map<String, TypedValue>> rows, long total, String dataTime, String mode, List<ReportCalculator.SummaryValue> summaries) { }
    public record Access(ReportActor actor, Run run, Version version, Binding binding, Payload payload, ExportJob lease, String permission, String action) {
        public Access(ReportActor actor, Run run, Version version, Binding binding, Payload payload) { this(actor, run, version, binding, payload, null, EXPORT, "EXPORT"); }
        public Access(ReportActor actor, Run run, Version version, Binding binding, Payload payload, ExportJob lease) { this(actor, run, version, binding, payload, lease, EXPORT, "EXPORT"); }
    }
    public Payload payload(Run run) {
        try { return Objects.requireNonNull(json.readValue(run.parameters(), Payload.class)); }
        catch (Exception invalid) { throw new ReportFault("SNAPSHOT_INVALID", "报表运行配置无法解析", 500); }
    }
    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new ReportFault("SERIALIZATION_FAILED", "运行配置无法序列化", 500); }
    }

    public Run create(ReportActor actor, String report, String requestedMode, Map<String, Object> input) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        Version version = definitions.published(current, report, "VIEW", PREVIEW);
        Binding binding = definitions.bind(current, definitions.versionDsl(version));
        String mode = binding.compiled().dsl().kind() == ReportDsl.Kind.DOCUMENT ? "SNAPSHOT" : requestedMode;
        if (mode == null || !Set.of("LIVE", "SNAPSHOT").contains(mode)) throw new ReportFault("INVALID_PARAMETER", "运行模式无效");
        if (mode.equals("LIVE") && (binding.compiled().aggregateExpressions()
            || binding.compiled().dsl().summaries().stream().anyMatch(summary -> !summary.function().equals("COUNT_ROWS") || !summary.groupBy().isEmpty()))) {
            throw new ReportFault("SNAPSHOT_REQUIRED", "聚合公式或分组汇总需要快照运行");
        }
        Map<String, Object> parameters = definitions.parameters(binding, input);
        String scope = definitions.fingerprint(current, binding);
        Payload payload = new Payload(parameters, artifacts.storeId(), null, List.of());
        if (mode.equals("SNAPSHOT")) {
            var probe = binding.provider().query(binding.compiled().context(current), new DatasetQuery(parameters, binding.compiled().requiredFields(), 1, 1, false, null, false));
            if (probe.total() > 5000) throw new ReportFault("CAPACITY_EXCEEDED", "交互快照最多五千行；大型列表请使用实时分页和明细导出");
        }
        current = definitions.resolve(current.tenantId(), current.userId());
        definitions.definition(current, version.reportId(), "VIEW", PREVIEW);
        binding = definitions.bind(current, definitions.versionDsl(version));
        if (!scope.equals(definitions.fingerprint(current, binding))) throw scopeChanged();
        Run run = store.createRun(current, version.reportId(), version.id(), mode, json(payload), scope,
            Instant.now().plusSeconds(properties.getRetentionHours() * 3600L));
        if (mode.equals("LIVE")) {
            if (!store.transitionRun(current.tenantId(), run.id(), "RUNNING", null, null, 0, json(payload), null)
                || !store.transitionRun(current.tenantId(), run.id(), "SUCCEEDED", null, null, 0, json(payload), null)) throw buildConflict();
        } else {
            buildSnapshot(new Access(current, run, version, binding, payload, null, PREVIEW, "VIEW"), new ReportCancellation(), 5000, PREVIEW, "VIEW");
        }
        store.audit(current, version.reportId(), "RUN", "SUCCEEDED", null);
        return store.run(current.tenantId(), run.id()).orElseThrow(ReportRuntimeService::missing);
    }

    public Access access(ReportActor actor, String id, String permission, String action) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        purpose(permission, action);
        Run run = store.run(current.tenantId(), id).orElseThrow(ReportRuntimeService::missing);
        if (run.actor() != current.userId()) throw ReportFault.denied();
        if (!run.expires().isAfter(Instant.now()) || run.state().equals("EXPIRED")) throw expired();
        Definition definition = definitions.definition(current, run.reportId(), action, permission);
        if (!definition.status().equals("PUBLISHED")) throw new ReportFault("REPORT_OFFLINE", "报表已经下线", 409);
        Version version = store.version(current.tenantId(), run.version()).orElseThrow(ReportRuntimeService::missing);
        if (!version.reportId().equals(run.reportId())) throw missing();
        Binding binding = definitions.bind(current, definitions.versionDsl(version));
        if ("EXPORT".equals(action)) definitions.requireDatasetExport(current, binding);
        if (!Objects.equals(run.scopeHash(), definitions.fingerprint(current, binding))) throw scopeChanged();
        Payload payload = payload(run);
        if (!artifacts.storeId().equals(payload.store())) throw new ReportFault("STORAGE_NODE_MISMATCH", "当前存储节点没有该快照，请在原节点访问或启用共享存储", 409);
        return new Access(current, run, version, binding, payload, null, permission, action);
    }
    private static void purpose(String permission, String action) {
        if (!(PREVIEW.equals(permission) && "VIEW".equals(action) || EXPORT.equals(permission) && "EXPORT".equals(action))) throw ReportFault.denied();
    }
    private Access recheck(Access previous) {
        if (previous.lease() != null && !store.leaseValid(previous.lease())) throw leaseLost();
        Access current = access(previous.actor(), previous.run().id(), previous.permission(), previous.action());
        if (previous.lease() != null && !store.leaseValid(previous.lease())) throw leaseLost();
        return new Access(current.actor(), current.run(), current.version(), current.binding(), current.payload(), previous.lease(), previous.permission(), previous.action());
    }
    public Run status(ReportActor actor, String id) { return access(actor, id, PREVIEW, "VIEW").run(); }

    public PageData page(ReportActor actor, String id, int page, int size, String sort, boolean descending) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 200) throw new ReportFault("INVALID_PARAMETER", "分页范围无效");
        Access access = access(actor, id, PREVIEW, "VIEW");
        Run run = access.run(); Binding binding = access.binding(); ready(run);
        PageData result;
        if (run.mode().equals("SNAPSHOT")) {
            if (sort != null) throw new ReportFault("INVALID_PARAMETER", "快照顺序已经固定，排序请建立新运行");
            snapshots.verify(run.tenant(), run.snapshot(), access.payload().checksum());
            recheck(access);
            var rows = snapshots.page(run.tenant(), run.snapshot(), (long) (page - 1) * size, size);
            result = new PageData(binding.compiled().dsl().columns(), ReportDefinitionService.project(rows, binding.compiled().dsl()), run.rows(),
                run.dataTime().toString(), "SNAPSHOT", access.payload().summaries());
        } else {
            if (sort != null && binding.compiled().dsl().columns().stream().noneMatch(column -> column.field().equals(sort) && !binding.compiled().expressions().containsKey(sort))) {
                throw new ReportFault("INVALID_PARAMETER", "只能按当前可见的可信字段排序");
            }
            var source = binding.provider().query(binding.compiled().context(access.actor()), new DatasetQuery(access.payload().input(), binding.compiled().requiredFields(), page, size, false, sort, descending));
            if (source.rows().size() > size) throw ReportFault.capacity();
            var calculated = new ReportCalculator().calculate(binding.compiled(), source.rows(), access.payload().input());
            var summaries = binding.compiled().dsl().summaries().stream().map(summary -> new ReportCalculator.SummaryValue(summary.label(), Map.of(), TypedValue.of(ValueType.INTEGER, source.total()))).toList();
            result = new PageData(binding.compiled().dsl().columns(), ReportDefinitionService.project(calculated.rows(), binding.compiled().dsl()), source.total(), source.dataTime().toString(), "LIVE", summaries);
        }
        recheck(access);
        return result;
    }

    public RenderPlan layout(ReportActor actor, String id, String permission, String action) { return layout(access(actor, id, permission, action)); }
    public RenderPlan layout(Access previous) { return layout(previous, new ReportCancellation()); }
    public RenderPlan layout(Access previous, ReportCancellation cancellation) {
        cancellation.check();
        Access access = recheck(previous);
        Run run = access.run(); ready(run);
        if (!run.mode().equals("SNAPSHOT")) throw new ReportFault("SNAPSHOT_REQUIRED", "文档排版需要完成的快照运行");
        if (run.rows() > 5000) throw ReportFault.capacity();
        snapshots.verify(run.tenant(), run.snapshot(), access.payload().checksum());
        access = recheck(access); cancellation.check();
        ReportRenderPlanStore.Artifact candidate = null;
        try {
            if (access.run().plan() == null) {
                var rows = snapshots.page(run.tenant(), run.snapshot(), 0, 5000);
                var data = new ReportCalculator.CalculatedData(rows, access.payload().summaries());
                Access measured = access;
                candidate = plans.create(run.tenant(), run.id(), access.payload().checksum(), access.version().engine(),
                    FontSupport.find(properties.getFontPath()), cancellation,
                    font -> new DocumentLayoutEngine().layout(measured.binding().compiled(), data, measured.payload().input(), font));
                cancellation.check(); access = recheck(access);
                Payload payload = new Payload(access.payload().input(), access.payload().store(), access.payload().checksum(), access.payload().summaries(), candidate.checksum());
                boolean registered = store.publishRenderPlan(run.tenant(), run.id(), run.snapshot(), candidate.reference(), json(payload), access.lease());
                access = recheck(access);
                if (!registered && access.run().plan() == null) throw buildConflict();
            }
            RenderPlan plan = plans.read(run.tenant(), access.run().plan(), access.payload().planChecksum(), run.id(),
                access.payload().checksum(), access.version().engine(), run.rows(), cancellation);
            cancellation.check(); recheck(access);
            return plan;
        } finally {
            if (candidate != null) {
                Run current = store.run(run.tenant(), run.id()).orElse(null);
                if (current == null || !candidate.reference().equals(current.plan())) artifacts.delete(run.tenant(), candidate.reference());
            }
        }
    }

    /** Only server-side exporters receive the private, captured font; no physical path is exposed by the API. */
    public void withLayoutFont(Access previous, ReportCancellation cancellation, java.util.function.Consumer<java.nio.file.Path> action) {
        cancellation.check();
        Access access = recheck(previous); ready(access.run());
        if (access.run().plan() == null) throw new ReportFault("PLAN_NOT_READY", "排版产物尚未生成", 409);
        snapshots.verify(access.run().tenant(), access.run().snapshot(), access.payload().checksum());
        recheck(access);
        plans.withFont(access.run().tenant(), access.run().plan(), access.payload().planChecksum(), access.run().id(),
            access.payload().checksum(), access.version().engine(), access.run().rows(), cancellation,
            font -> { cancellation.check(); recheck(access); action.accept(font); cancellation.check(); recheck(access); });
        cancellation.check(); recheck(access);
    }

    public ExportJob enqueue(ReportActor actor, String origin, String format, String requestKey) {
        if (format == null || !Set.of("PDF", "XLSX_DATA", "XLSX_LAYOUT").contains(format) || requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new ReportFault("INVALID_PARAMETER", "导出类型或请求标识无效");
        }
        Access access = access(actor, origin, EXPORT, "EXPORT"); ready(access.run());
        String fingerprint = ReportDefinitionService.hash(access.run().id() + ":" + format);
        var prior = store.jobByRequest(access.actor(), requestKey);
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(fingerprint) || !prior.get().format().equals(format)) throw new ReportFault("REVISION_CONFLICT", "同一请求标识不能更改运行或格式", 409);
            return prior.get();
        }
        recheck(access);
        ExportJob job = store.createExportJob(access.actor(), access.run().id(), format, requestKey, fingerprint, access.run().expires());
        store.audit(access.actor(), access.run().reportId(), "EXPORT_ENQUEUE", "SUCCEEDED", null);
        return job;
    }
    public ExportJob job(ReportActor actor, String id) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, EXPORT);
        ExportJob job = store.job(current.tenantId(), id).orElseThrow(ReportRuntimeService::missing);
        if (job.actor() != current.userId()) throw ReportFault.denied();
        access(current, job.runId(), EXPORT, "EXPORT");
        return job;
    }
    public List<ExportJob> jobs(ReportActor actor) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, EXPORT);
        // List only still-authorized resources; execution/download retain their full scope checks.
        return store.jobs(current).stream().filter(job -> {
            try { access(current, job.runId(), EXPORT, "EXPORT"); return true; }
            catch (ReportFault denied) { return false; }
        }).toList();
    }
    public void cancel(ReportActor actor, String id) {
        ReportActor current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, EXPORT);
        ExportJob job = store.job(current.tenantId(), id).orElseThrow(ReportRuntimeService::missing);
        if (job.actor() != current.userId()) throw ReportFault.denied();
        store.cancel(current, id); store.audit(current, null, "EXPORT_CANCEL", "REQUESTED", null);
    }
    public Access execution(ExportJob claimed) {
        if (!claimed.expires().isAfter(Instant.now())) throw expired();
        if (!store.leaseValid(claimed)) throw leaseLost();
        ReportActor current = definitions.resolve(claimed.tenant(), claimed.actor());
        Access access = access(current, claimed.runId(), EXPORT, "EXPORT");
        if (!store.leaseValid(claimed)) throw leaseLost();
        return new Access(access.actor(), access.run(), access.version(), access.binding(), access.payload(), claimed, EXPORT, "EXPORT");
    }

    /** Worker-compatible default: building export snapshots always requires EXPORT. */
    public Access buildSnapshot(Access access, ReportCancellation cancellation, int maximum) {
        return buildSnapshot(access, cancellation, maximum, EXPORT, "EXPORT");
    }
    public Access buildSnapshot(Access previous, ReportCancellation cancellation, int maximum, String permission, String action) {
        purpose(permission, action); cancellation.check();
        Access purposeAccess = new Access(previous.actor(), previous.run(), previous.version(), previous.binding(), previous.payload(), previous.lease(), permission, action);
        Access access = recheck(purposeAccess);
        Run run = access.run();
        if (run.state().equals("SUCCEEDED") && run.snapshot() != null) {
            snapshots.verify(run.tenant(), run.snapshot(), access.payload().checksum()); cancellation.check();
            return recheck(access);
        }
        if (EXPORT.equals(permission) && access.lease() == null) throw leaseLost();
        String checkpoint = access.lease() == null ? "build/preview/" + UUID.randomUUID()
            : "build/job/" + access.lease().id() + "/" + access.lease().attempts() + "/" + UUID.randomUUID();
        if (!store.beginSnapshot(run.tenant(), run.id(), checkpoint, access.lease())) throw buildConflict();
        ReportSnapshotStore.Snapshot snapshot = null;
        try {
            cancellation.check(); access = recheck(access);
            snapshot = snapshots.create(access.binding().compiled(), access.binding().provider(), access.actor(), access.payload().input(), cancellation, maximum);
            cancellation.check(); access = recheck(access); cancellation.check();
            Payload payload = new Payload(access.payload().input(), artifacts.storeId(), snapshot.checksum(), snapshot.summaries());
            if (!store.finishSnapshot(run.tenant(), run.id(), checkpoint, access.lease(), "SUCCEEDED", snapshot.reference(), snapshot.rows(), json(payload), null)) throw leaseLost();
            return recheck(new Access(access.actor(), store.run(run.tenant(), run.id()).orElseThrow(ReportRuntimeService::missing), access.version(), access.binding(), payload, access.lease(), permission, action));
        } catch (RuntimeException failure) {
            if (snapshot != null) {
                Run current = store.run(run.tenant(), run.id()).orElse(null);
                // A successful canonical snapshot is retained even if delivery was subsequently revoked.
                if (current == null || !snapshot.reference().equals(current.snapshot())) artifacts.delete(run.tenant(), snapshot.reference());
            }
            String error = code(failure);
            String state = access.lease() != null && retryable(error) ? "QUEUED" : error.equals("CANCELLED") ? "CANCELLED" : "FAILED";
            store.finishSnapshot(run.tenant(), run.id(), checkpoint, access.lease(), state, null, 0, run.parameters(), error);
            throw failure;
        }
    }
    public void each(Access previous, java.util.function.Consumer<Map<String, TypedValue>> consumer, ReportCancellation cancellation) {
        cancellation.check(); Access access = recheck(previous); ready(access.run());
        if (!access.run().mode().equals("SNAPSHOT")) throw new ReportFault("SNAPSHOT_REQUIRED", "导出需要固定快照");
        snapshots.verify(access.run().tenant(), access.run().snapshot(), access.payload().checksum());
        cancellation.check(); recheck(access);
        long[] rows = {0};
        snapshots.each(access.run().tenant(), access.run().snapshot(), row -> {
            cancellation.check();
            if (rows[0]++ % 128 == 0) recheck(access);
            consumer.accept(row);
        }, cancellation);
        cancellation.check(); recheck(access);
    }
    private static void ready(Run run) { if (!run.state().equals("SUCCEEDED")) throw new ReportFault("RUN_NOT_READY", "报表运行尚未完成", 409); }
    private static boolean retryable(String code) { return Set.of("STORAGE_UNAVAILABLE", "ARTIFACT_STORE_UNAVAILABLE", "XLSX_EXPORT_FAILED", "PDF_EXPORT_FAILED", "REPORT_EXECUTION_FAILED").contains(code); }
    public static String code(Throwable failure) {
        String code = failure instanceof ReportFault fault ? fault.code() : failure instanceof DatasetAccessException fault ? fault.code() : "REPORT_EXECUTION_FAILED";
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,63}") ? code : "REPORT_EXECUTION_FAILED";
    }
    private static ReportFault expired() { return new ReportFault("SNAPSHOT_EXPIRED", "报表运行已经过期", 410); }
    private static ReportFault scopeChanged() { return new ReportFault("SCOPE_CHANGED", "权限范围已变化，请重新运行报表", 403); }
    private static ReportFault leaseLost() { return new ReportFault("LEASE_LOST", "导出任务租约已变化或执行已停止", 409); }
    private static ReportFault buildConflict() { return new ReportFault("SNAPSHOT_BUILD_IN_PROGRESS", "快照正在构建或状态已经变化", 409); }
    private static ReportFault missing() { return new ReportFault("NOT_FOUND", "报表运行不存在或不可访问", 404); }
}
