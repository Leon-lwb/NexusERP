package com.nexus.report.batch;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

/** 批量单据固定文档集合，每个单据独立鉴权、任务、失败和重试。 */
public final class ReportBatchService {
    /**
     * 一个单据绑定明确报表发布版本与规范化参数。
     *
     * @param reportVersionId 绑定的不可变报表发布版本
     * @param parameters 固定参数契约
     */
    public record Document(String reportVersionId, Map<String, Object> parameters) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param reportVersionId 当前受控操作的reportVersionId输入
         * @param parameters 当前受控操作的parameters输入
         */
        public Document {
            parameters =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
        }
    }

    /**
     * 只向界面返回已重新鉴权的任务标识，不暴露其他人的原始参数。
     *
     * @param number 当前契约的number配置或状态
     * @param reportVersionId 绑定的不可变报表发布版本
     * @param jobId 当前契约的jobId配置或状态
     * @param state 任务或资源状态
     * @param error 当前契约的error配置或状态
     * @param canRetry 当前主体能否通过原运行服务重试任务
     * @param retryRound 原运行任务的重试轮次
     * @param expires 原运行任务的到期时间，未鉴权时不返回
     */
    public record Item(
            int number, String reportVersionId, String jobId, String state, String error,
            boolean canRetry, int retryRound, Instant expires) {}

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前store状态或配置。 */
    private final JdbcReportStore store;

    /** 保存当前definitions状态或配置。 */
    private final ReportDefinitionService definitions;

    /** 保存当前runtime状态或配置。 */
    private final ReportRuntimeService runtime;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /**
     * 注入统一版本和导出队列，不建立第二套任务执行器。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param json 固定版本的契约正文
     */
    public ReportBatchService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler json) {
        this.resources = resources;
        this.jdbc = jdbc;
        this.store = store;
        this.definitions = definitions;
        this.runtime = runtime;
        this.json = json;
    }

    /**
     * 创建固定批次前核对每个单据的参数、版本和资源权限。
     *
     * @param actor 执行用户
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param documents 当前契约的documents配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft create(
            ReportActor actor, String code, String title, List<Document> documents) {
        var current = current(actor, "report:batch:manage");
        String normalizedTitle = title == null ? null : title.strip();
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")
                || normalizedTitle == null || normalizedTitle.isBlank() || normalizedTitle.length() > 100)
            throw ReportFault.invalid("资源编码或标题无效");
        byCode(current, code).ifPresent(existing -> {
            if (existing.owner() != current.userId()) throw codeConflict();
        });
        if (documents == null || documents.isEmpty() || documents.size() > 50)
            throw ReportFault.invalid("批次单据范围1–50");
        var fixed = new ArrayList<Document>();
        for (var document : documents) {
            if (document == null) throw ReportFault.invalid("批次单据不能为空");
            var version = version(current, document.reportVersionId());
            var binding = definitions.bindVersion(current, version);
            fixed.add(
                    new Document(
                            version.id(),
                            ReportRuntimeService.parametersForStorage(
                                    definitions.parameters(binding, document.parameters()))));
        }
        String content = json.json(fixed);
        try {
            return resources.atomic(
                () -> {
                    var refreshed = current(current, "report:batch:manage");
                    var existing = byCode(refreshed, code);
                    if (existing.isPresent()) return sameCreation(refreshed, existing.get(), normalizedTitle, content);
                    var draft = resources.create("BATCH", refreshed, code, normalizedTitle, content);
                    resources.publish("BATCH", refreshed, draft.id(), draft.revision(), "{}");
                    for (int i = 0; i < fixed.size(); i++) {
                        var document = fixed.get(i);
                        version(refreshed, document.reportVersionId());
                        jdbc.update(
                                "INSERT INTO"
                                    + " rpt_batch_item(batch_id,tenant_id,item_no,report_version_id,parameters_json)"
                                    + " VALUES(?,?,?,?,?)",
                                Long.parseLong(draft.id()),
                                refreshed.tenantId(),
                                i,
                                Long.parseLong(document.reportVersionId()),
                                json.json(document.parameters()));
                    }
                    return resources.read("BATCH", refreshed.tenantId(), draft.id()).orElseThrow();
                });
        } catch (DuplicateKeyException raced) {
            return resources.atomic(() -> {
                var refreshed = current(current, "report:batch:manage");
                var existing = byCode(refreshed, code).orElseThrow(() -> raced);
                return sameCreation(refreshed, existing, normalizedTitle, content);
            });
        }
    }

    /**
     * 仅以当前租户的唯一编码读取批次，不能跨租户复用创建记录。
     *
     * @param actor 已刷新身份
     * @param code 已校验的批次编码
     * @return 当前租户的既有批次
     */
    private Optional<ReportResourceStore.Draft> byCode(ReportActor actor, String code) {
        return jdbc.queryForList("SELECT batch_id FROM rpt_batch WHERE tenant_id=? AND code=?",
                        String.class, actor.tenantId(), code).stream().findFirst()
                .flatMap(id -> resources.read("BATCH", actor.tenantId(), id));
    }

    /**
     * 仅允许原创建者以相同标题和规范化单据重放创建请求。
     *
     * @param actor 已刷新身份
     * @param existing 已提交的批次
     * @param title 规范化标题
     * @param content 规范化单据内容
     * @return 原批次及其当前状态
     */
    private ReportResourceStore.Draft sameCreation(ReportActor actor, ReportResourceStore.Draft existing,
            String title, String content) {
        if (existing.owner() != actor.userId() || !Objects.equals(existing.title(), title)
                || !Objects.equals(json.read(existing.json(), List.class), json.read(content, List.class)))
            throw codeConflict();
        return existing;
    }

    /**
     * 返回不会泄露其他创建者或请求内容的编码冲突。
     *
     * @return 可供调用方识别的冲突异常
     */
    private static ReportFault codeConflict() {
        return new ReportFault("BATCH_CODE_CONFLICT", "批次编码已被其他创建者或不同内容使用", 409);
    }

    /**
     * 每个单据的入队事务锁定自身行，重复点击或节点重试不重复创建有效任务。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     */
    public void start(ReportActor actor, String id) {
        var current = current(actor, "report:batch:manage");
        var batch = resources.access("BATCH", current, id, "MANAGE");
        if (batch.owner() != current.userId()) throw ReportFault.denied();
        if (!batch.status().equals("PUBLISHED"))
            throw new ReportFault("BATCH_OFFLINE", "批次已停用", 409);
        var indices =
                jdbc.queryForList(
                        "SELECT item_no FROM rpt_batch_item WHERE tenant_id=? AND batch_id=? ORDER"
                                + " BY item_no",
                        Integer.class,
                        current.tenantId(),
                        Long.parseLong(id));
        for (int index : indices) {
            try {
                resources.atomic(
                        () -> {
                            var refreshed = current(current, "report:batch:manage");
                            resources.access("BATCH", refreshed, id, "MANAGE");
                            var row =
                                    jdbc.queryForMap(
                                            "SELECT * FROM rpt_batch_item WHERE tenant_id=? AND"
                                                    + " batch_id=? AND item_no=? FOR UPDATE",
                                            refreshed.tenantId(),
                                            Long.parseLong(id),
                                            index);
                            if (row.get("job_id") != null) {
                                runtime.job(refreshed, row.get("job_id").toString());
                                return null;
                            }
                            String versionId = row.get("report_version_id").toString();
                            version(refreshed, versionId);
                            var input = json.read(row.get("parameters_json").toString(), Map.class);
                            var job =
                                    runtime.enqueueFixed(
                                            refreshed,
                                            versionId,
                                            "PDF",
                                            input,
                                            "batch_" + id + "_" + index);
                            jdbc.update(
                                    "UPDATE rpt_batch_item SET"
                                            + " job_id=?,state='QUEUED',error_code=NULL WHERE"
                                            + " tenant_id=? AND batch_id=? AND item_no=?",
                                    Long.parseLong(job.id()),
                                    refreshed.tenantId(),
                                    Long.parseLong(id),
                                    index);
                            return null;
                        });
            } catch (ReportFault | DatasetAccessException failure) {
                jdbc.update(
                        "UPDATE rpt_batch_item SET state='FAILED',error_code=? WHERE tenant_id=?"
                                + " AND batch_id=? AND item_no=?",
                        com.nexus.report.runtime.ReportRuntimeService.code(failure),
                        current.tenantId(),
                        Long.parseLong(id),
                        index);
            }
        }
    }

    /**
     * 列出批次时仍刷新权限；一份撤权不暴露该份的领取入口。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public List<Item> items(ReportActor actor, String id) {
        var current = current(actor, "report:batch:list");
        ReportDefinitionService.require(current, "report:runtime:preview");
        resources.access("BATCH", current, id, "VIEW");
        var result = new ArrayList<Item>();
        for (var row :
                jdbc.queryForList(
                        "SELECT * FROM rpt_batch_item WHERE tenant_id=? AND batch_id=? ORDER BY"
                                + " item_no",
                        current.tenantId(),
                        Long.parseLong(id))) {
            String jobId = row.get("job_id") == null ? null : row.get("job_id").toString();
            String state = row.get("state").toString(),
                    error = Objects.toString(row.get("error_code"), null);
            boolean canRetry = false;
            int retryRound = 0;
            Instant expires = null;
            try {
                version(current, row.get("report_version_id").toString());
                if (jobId != null) {
                    var job = runtime.job(current, jobId);
                    state = job.expires().isAfter(Instant.now()) ? job.state() : "EXPIRED";
                    error = job.error();
                    canRetry = JdbcReportStore.manualRetryable(job);
                    retryRound = job.retryRound();
                    expires = job.expires();
                }
            } catch (ReportFault denied) {
                jobId = null;
                state = "BLOCKED";
                error = denied.code();
            }
            result.add(
                    new Item(
                            ((Number) row.get("item_no")).intValue(),
                            row.get("report_version_id").toString(),
                            jobId,
                            state,
                            error, canRetry, retryRound, expires));
        }
        return List.copyOf(result);
    }

    /**
     * 已经逐项重鉴权的固定来源，不向HTTP响应暴露私有引用。
     *
     * @param number 原批次项序号
     * @param reportVersionId 固定报表发布版本
     * @param job 同一创建者拥有的成功PDF任务
     */
    record PdfItem(int number, String reportVersionId, ReportRecords.ExportJob job) {}

    /**
     * 合并及流式领取持续复核的批次快照。
     *
     * @param batch 已上线并通过VIEW检查的批次
     * @param items 按原序排列且全部可领取的任务
     */
    record PdfBatch(ReportResourceStore.Draft batch, List<PdfItem> items) {}

    /**
     * 只读取固定版本和已存在任务，不创建任务或重新执行业务查询。
     *
     * @param actor 当前请求主体
     * @param id 批次标识
     * @return 完整可领取的批次和来源快照
     */
    PdfBatch pdfBatch(ReportActor actor, String id) {
        var current = current(actor, "report:batch:list");
        ReportDefinitionService.require(current, "report:runtime:preview");
        ReportDefinitionService.require(current, "report:runtime:export");
        var batch = resources.access("BATCH", current, id, "VIEW");
        if (batch.owner() != current.userId()) throw ReportFault.denied();
        if (!"PUBLISHED".equals(batch.status()))
            throw new ReportFault("BATCH_OFFLINE", "批次已停用", 409);
        var documents = json.read(batch.json(), Document[].class);
        var rows = jdbc.queryForList("SELECT * FROM rpt_batch_item WHERE tenant_id=? AND batch_id=? ORDER BY item_no",
                current.tenantId(), Long.parseLong(id));
        if (documents.length == 0 || documents.length > 50 || rows.size() != documents.length)
            throw new ReportFault("BATCH_ITEM_NOT_READY", "批次固定单据集合不完整，请创建新批次", 409);
        var result = new ArrayList<PdfItem>();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            int number = ((Number) row.get("item_no")).intValue();
            try {
                String versionId = row.get("report_version_id").toString();
                if (number != index || !versionId.equals(documents[index].reportVersionId())
                        || !json.read(row.get("parameters_json").toString(), Map.class).equals(documents[index].parameters()))
                    throw new ReportFault("BATCH_ITEM_CHANGED", "固定单据集合已变化，请创建新批次", 409);
                version(current, versionId);
                if (row.get("job_id") == null)
                    throw new ReportFault("BATCH_ITEM_NOT_READY", "单据尚未生成导出任务", 409);
                var job = runtime.job(current, row.get("job_id").toString());
                if (!job.expires().isAfter(Instant.now()) || "EXPIRED".equals(job.state()))
                    throw new ReportFault("BATCH_ITEM_EXPIRED", "单据已过期，请创建新批次", 410);
                if (job.cancelled() || "CANCELLED".equals(job.state()))
                    throw new ReportFault("BATCH_ITEM_CANCELLED", "单据已取消，请创建新批次", 409);
                if ("FAILED".equals(job.state()))
                    throw new ReportFault("BATCH_ITEM_FAILED", "单据导出失败：" + Objects.toString(job.error(), "REPORT_EXECUTION_FAILED"), 409);
                if (!"SUCCEEDED".equals(job.state()) || job.file() == null)
                    throw new ReportFault("BATCH_ITEM_NOT_READY", "单据导出尚未完成", 409);
                if (!"PDF".equals(job.format()))
                    throw new ReportFault("BATCH_ITEM_FORMAT", "单据产物不是PDF", 409);
                var preview = runtime.access(current, job.runId(), "report:runtime:preview", "VIEW");
                if (!preview.run().version().equals(versionId)
                        || !job.requestKey().equals("batch_" + id + "_" + number)
                        || !ReportRuntimeService.parametersForStorage(preview.payload().input()).equals(documents[index].parameters()))
                    throw new ReportFault("BATCH_ITEM_CHANGED", "来源任务不属于固定单据", 409);
                result.add(new PdfItem(number, versionId, job));
            } catch (ReportFault failure) {
                throw itemFault(number, failure);
            } catch (DatasetAccessException failure) {
                throw itemFault(number, new ReportFault(ReportRuntimeService.code(failure), "单据数据权限已变化", 403));
            }
        }
        return new PdfBatch(batch, List.copyOf(result));
    }

    /**
     * 用原始项序号定位错误，不包含参数、私有路径或其他主体信息。
     *
     * @param number 批次项序号
     * @param failure 原受控异常
     * @return 保留状态码和错误码的定位异常
     */
    static ReportFault itemFault(int number, ReportFault failure) {
        return new ReportFault(failure.code(), "第" + (number + 1) + "项（item_no=" + number + "）：" + failure.getMessage(), failure.status());
    }

    /**
     * 只列出资源ACL允许访问的批次。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<ReportResourceStore.Draft> list(ReportActor actor) {
        var current = current(actor, "report:batch:list");
        return resources.list("BATCH", current).stream()
                .map(
                        d ->
                                new ReportResourceStore.Draft(
                                        d.id(),
                                        d.tenant(),
                                        d.code(),
                                        d.title(),
                                        null,
                                        d.revision(),
                                        d.publishedVersion(),
                                        d.status(),
                                        d.owner()))
                .toList();
    }

    /**
     * 固定单据发布仍要求当前VIEW、EXPORT及所有业务字段权限。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    private com.nexus.report.domain.ReportRecords.Version version(ReportActor actor, String id) {
        var version =
                store.version(actor.tenantId(), id)
                        .orElseThrow(() -> new ReportFault("NOT_FOUND", "批次单据版本不存在", 404));
        var definition =
                definitions.definition(
                        actor, version.reportId(), "EXPORT", "report:runtime:export");
        definitions.definition(actor, version.reportId(), "VIEW", "report:runtime:preview");
        if (!definition.status().equals("PUBLISHED")
                || definitions.versionDsl(version).kind() != ReportDsl.Kind.DOCUMENT)
            throw ReportFault.invalid("批次仅允许仍上线的固定单据版本");
        definitions.bindVersion(actor, version);
        return version;
    }

    /**
     * 从授权端口刷新当前主体。
     *
     * @param actor 执行用户
     * @param permission 当前契约的permission配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportActor current(ReportActor actor, String permission) {
        var current = definitions.resolve(actor.tenantId(), actor.userId());
        ReportDefinitionService.require(current, permission);
        return current;
    }
}
