package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.RenderPlan;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** 运行、分页、布局、持久导出及认证下载入口，响应不暴露私有路径。 */
@RestController
@RequestMapping("/report/runtime")
public class ReportRuntimeController {
    /** 保存当前 {@code actors} 值。 */
    private final ReportRequestActor actors;

    /** 保存当前 {@code runtime} 值。 */
    private final ReportRuntimeService runtime;

    /** 保存当前 {@code artifacts} 值。 */
    private final ReportArtifactStore artifacts;

    /**
     * 初始化报表运行接口并保存所需依赖或不可变值。
     *
     * @param actors 当前操作的 {@code actors} 输入
     * @param runtime 当前操作的 {@code runtime} 输入
     * @param artifacts 当前操作的 {@code artifacts} 输入
     */
    public ReportRuntimeController(
            ReportRequestActor actors,
            ReportRuntimeService runtime,
            ReportArtifactStore artifacts) {
        this.actors = actors;
        this.runtime = runtime;
        this.artifacts = artifacts;
    }

    /**
     * 运行请求，可选预期版本用于在查询和创建运行前核对发布版本。
     *
     * @param reportId 当前契约的reportId配置或状态
     * @param mode 当前契约的mode配置或状态
     * @param parameters 固定参数契约
     * @param expectedVersionId 调用方确认的发布版本，省略时保持旧调用语义
     */
    public record RunRequest(
            String reportId,
            String mode,
            Map<String, Object> parameters,
            String expectedVersionId) {
        /**
         * 保留旧三参数构造入口，不指定预期版本。
         *
         * @param reportId 报表标识
         * @param mode 运行一致性模式
         * @param parameters 固定参数契约
         */
        public RunRequest(String reportId, String mode, Map<String, Object> parameters) {
            this(reportId, mode, parameters, null);
        }
    }

    /**
     * Retry请求，负责当前模块对应的契约或处理职责。
     *
     * @param retryRound 当前契约的retryRound配置或状态
     */
    public record RetryRequest(Integer retryRound) {}

    /**
     * 导出请求，负责当前模块对应的契约或处理职责。
     *
     * @param format 受控格式
     * @param requestKey 当前契约的requestKey配置或状态
     */
    public record ExportRequest(String format, String requestKey) {}

    /**
     * RunView，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param reportId 当前契约的reportId配置或状态
     * @param version 当前契约的version配置或状态
     * @param mode 当前契约的mode配置或状态
     * @param state 任务或资源状态
     * @param rows 精确类型的数据行
     * @param dataTime 当前契约的dataTime配置或状态
     * @param expires 当前契约的expires配置或状态
     * @param error 当前契约的error配置或状态
     */
    public record RunView(
            String id,
            String reportId,
            String version,
            String mode,
            String state,
            long rows,
            Instant dataTime,
            Instant expires,
            String error) {}

    /**
     * 任务View，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param runId 当前契约的runId配置或状态
     * @param format 受控格式
     * @param state 任务或资源状态
     * @param attempts 当前契约的attempts配置或状态
     * @param bytes 保留或实际占用的字节数
     * @param expires 当前契约的expires配置或状态
     * @param error 当前契约的error配置或状态
     * @param phase 当前契约的phase配置或状态
     * @param processedRows 当前契约的processedRows配置或状态
     * @param totalRows 当前契约的totalRows配置或状态
     * @param retryRound 当前契约的retryRound配置或状态
     * @param heartbeat 当前契约的heartbeat配置或状态
     * @param canRetry 当前契约的canRetry配置或状态
     */
    public record JobView(
            String id,
            String runId,
            String format,
            String state,
            int attempts,
            Long bytes,
            Instant expires,
            String error,
            String phase,
            long processedRows,
            Long totalRows,
            int retryRound,
            Instant heartbeat,
            boolean canRetry) {}

    /**
     * 完成 {@code view} 对应的报表运行接口处理。
     *
     * @param run 当前报表运行
     * @return 当前处理返回的固定结果
     */
    static RunView view(Run run) {
        return new RunView(
                run.id(),
                run.reportId(),
                run.version(),
                run.mode(),
                run.state(),
                run.rows(),
                run.dataTime(),
                run.expires(),
                run.error());
    }

    /**
     * 完成 {@code view} 对应的报表运行接口处理。
     *
     * @param job 当前导出任务
     * @return 当前处理返回的固定结果
     */
    static JobView view(ExportJob job) {
        return new JobView(
                job.id(),
                job.runId(),
                job.format(),
                job.state(),
                job.attempts(),
                job.bytes(),
                job.expires(),
                job.error(),
                job.phase(),
                job.processedRows(),
                job.totalRows(),
                job.retryRound(),
                job.heartbeat(),
                com.nexus.report.mapper.JdbcReportStore.manualRetryable(job));
    }

    /**
     * 完成 {@code templates} 对应的报表运行接口处理。
     *
     * @param datasetCode 当前操作的 {@code datasetCode} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/templates")
    public R<java.util.List<com.nexus.report.service.ReportDefinitionService.BusinessTemplate>>
            templates(@RequestParam String datasetCode) {
        return R.ok(runtime.businessTemplates(actors.current(), datasetCode));
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @PostMapping("/runs")
    public R<RunView> create(@RequestBody RunRequest body) {
        var actor = actors.current();
        var run =
                body.expectedVersionId() == null
                        ? runtime.create(actor, body.reportId(), body.mode(), body.parameters())
                        : runtime.create(
                                actor,
                                body.reportId(),
                                body.mode(),
                                body.parameters(),
                                body.expectedVersionId());
        return R.ok(view(run));
    }

    /**
     * 重新鉴权后读取运行状态。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/runs/{id}")
    public R<RunView> status(@PathVariable String id) {
        return R.ok(view(runtime.status(actors.current(), id)));
    }

    /**
     * 读取当前授权范围内的精确分页结果。
     *
     * @param id 资源标识
     * @param page 当前页
     * @param size 有界页大小
     * @param sort 当前操作的 {@code sort} 输入
     * @param descending 当前操作的 {@code descending} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/runs/{id}/page")
    public R<ReportRuntimeService.PageData> page(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean descending) {
        return R.ok(runtime.page(actors.current(), id, page, size, sort, descending));
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/runs/{id}/pivot")
    public R<com.nexus.report.pivot.AggregationExecutor.Result> pivot(@PathVariable String id) {
        return R.ok(runtime.pivot(actors.current(), id));
    }

    /**
     * 根据服务端坐标读取原始固定快照。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param coordinate 本次固定快照的服务端钻取坐标
     * @param offset 当前契约的offset配置或状态
     * @param size 当前契约的size配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/runs/{id}/drill")
    public R<com.nexus.report.pivot.AggregationExecutor.Drill> drill(
            @PathVariable String id,
            @RequestParam String coordinate,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int size) {
        return R.ok(runtime.drill(actors.current(), id, coordinate, offset, size));
    }

    /**
     * 返回服务端固定坐标。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/runs/{id}/layout")
    public R<RenderPlan> layout(@PathVariable String id) {
        return R.ok(runtime.layout(actors.current(), id, "report:runtime:preview", "VIEW"));
    }

    /**
     * 按幂等键和租户配额创建导出任务。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @PostMapping("/runs/{id}/exports")
    public R<JobView> enqueue(@PathVariable String id, @RequestBody ExportRequest body) {
        return R.ok(view(runtime.enqueue(actors.current(), id, body.format(), body.requestKey())));
    }

    /**
     * 完成 {@code jobs} 对应的报表运行接口处理。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @GetMapping("/exports")
    public R<List<JobView>> jobs() {
        return R.ok(
                runtime.jobs(actors.current()).stream()
                        .map(ReportRuntimeController::view)
                        .toList());
    }

    /**
     * 完成 {@code job} 对应的报表运行接口处理。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @GetMapping("/exports/{id}")
    public R<JobView> job(@PathVariable String id) {
        return R.ok(view(runtime.job(actors.current(), id)));
    }

    /**
     * 重新鉴权后请求原任务重试。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @PostMapping("/exports/{id}/retry")
    public R<Void> retry(@PathVariable String id, @RequestBody RetryRequest body) {
        if (body.retryRound() == null) throw ReportFault.invalid("请提供任务重试轮次");
        runtime.retry(actors.current(), id, body.retryRound());
        return R.ok();
    }

    /**
     * 请求取消当前用户拥有的任务。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @PostMapping("/exports/{id}/cancel")
    public R<Void> cancel(@PathVariable String id) {
        runtime.cancel(actors.current(), id);
        return R.ok();
    }

    /**
     * 完成 {@code file} 对应的报表运行接口处理。
     *
     * @param id 资源标识
     * @param response 当前操作的 {@code response} 输入
     */
    @SaCheckPermission("report:runtime:export")
    @GetMapping("/exports/{id}/file")
    public void file(@PathVariable String id, HttpServletResponse response) throws IOException {
        var actor = actors.current();
        var job = runtime.job(actor, id);
        if (!job.expires().isAfter(Instant.now()) || job.state().equals("EXPIRED"))
            throw new ReportFault("SNAPSHOT_EXPIRED", "导出文件已过期", 410);
        if (!job.state().equals("SUCCEEDED") || job.file() == null)
            throw new ReportFault("RUN_NOT_READY", "导出文件尚未生成", 409);
        try (var materialized = artifacts.materialize(actor.tenantId(), job.file())) {
            var file = materialized.path();
            runtime.job(actor, id);
            String extension = job.format().equals("PDF") ? "pdf" : "xlsx";
            long size = Files.size(file);
            long started = System.nanoTime();
            boolean delivered = false;
            try (var input = Files.newInputStream(file)) {
                response.setContentType(
                        extension.equals("pdf")
                                ? "application/pdf"
                                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Cache-Control", "no-store, private");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader(
                        "Content-Disposition",
                        ContentDisposition.attachment()
                                .filename("nexus-report-" + job.id() + "." + extension)
                                .build()
                                .toString());
                response.setContentLengthLong(size);
                byte[] chunk = new byte[65536];
                int length;
                long checked = 0;
                while ((length = input.read(chunk)) >= 0) {
                    if (length == 0) continue;
                    if (checked >= 1048576) {
                        runtime.job(actor, id);
                        checked = 0;
                    }
                    response.getOutputStream().write(chunk, 0, length);
                    checked += length;
                }
                runtime.job(actor, id);
                delivered = true;
            } finally {
                runtime.recordDelivery(delivered, System.nanoTime() - started);
            }
        }
    }
}
