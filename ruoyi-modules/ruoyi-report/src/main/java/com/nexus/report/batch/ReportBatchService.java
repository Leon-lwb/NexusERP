package com.nexus.report.batch;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.service.ReportDefinitionService;

import org.springframework.jdbc.core.JdbcTemplate;

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
     */
    public record Item(
            int number, String reportVersionId, String jobId, String state, String error) {}

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
        if (documents == null || documents.isEmpty() || documents.size() > 50)
            throw ReportFault.invalid("批次单据范围1–50");
        var fixed = new ArrayList<Document>();
        for (var document : documents) {
            var version = version(current, document.reportVersionId());
            var binding = definitions.bindVersion(current, version);
            fixed.add(
                    new Document(
                            version.id(),
                            ReportRuntimeService.parametersForStorage(
                                    definitions.parameters(binding, document.parameters()))));
        }
        return resources.atomic(
                () -> {
                    var refreshed = current(current, "report:batch:manage");
                    var draft = resources.create("BATCH", refreshed, code, title, json.json(fixed));
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
            try {
                version(current, row.get("report_version_id").toString());
                if (jobId != null) state = runtime.job(current, jobId).state();
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
                            error));
        }
        return List.copyOf(result);
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
