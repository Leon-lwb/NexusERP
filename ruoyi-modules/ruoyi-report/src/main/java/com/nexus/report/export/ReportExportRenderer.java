package com.nexus.report.export;

import com.nexus.common.report.ReportCancellation;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.ExportJob;
import com.nexus.report.engine.*;
import com.nexus.report.runtime.ReportRuntimeService;

import java.io.OutputStream;

/** 在导出适配器中扩展格式，保持报表定义和业务提供者契约稳定。 */
public final class ReportExportRenderer implements ReportExportWorker.Renderer {
    /** 保存当前 {@code runtime} 值。 */
    private final ReportRuntimeService runtime;

    /** 保存当前 {@code temporaryBudget} 值。 */
    private final long temporaryBudget;

    /**
     * 初始化报表导出Renderer并保存所需依赖或不可变值。
     *
     * @param runtime 当前操作的 {@code runtime} 输入
     * @param properties 当前操作的 {@code properties} 输入
     */
    public ReportExportRenderer(ReportRuntimeService runtime, ReportProperties properties) {
        this.runtime = runtime;
        this.temporaryBudget = properties.getMaxArtifactBytes();
    }

    /**
     * 向受控输出流写入固定报表结果。
     *
     * @param job 当前导出任务
     * @param access 当前操作的 {@code access} 输入
     * @param output 受控输出流
     * @param cancellation 执行取消信号
     */
    public void write(
            ExportJob job,
            ReportRuntimeService.Access access,
            OutputStream output,
            ReportCancellation cancellation) {
        long started = System.nanoTime();
        boolean success = false;
        try {
            render(job, access, output, cancellation);
            success = true;
        } finally {
            runtime.recordExport(success, System.nanoTime() - started);
        }
    }

    /**
     * 按固定布局生成导出文件。
     *
     * @param job 当前导出任务
     * @param access 当前操作的 {@code access} 输入
     * @param output 受控输出流
     * @param cancellation 执行取消信号
     */
    private void render(
            ExportJob job,
            ReportRuntimeService.Access access,
            OutputStream output,
            ReportCancellation cancellation) {
        if (job.format().equals("XLSX_DATA")
                && access.binding().compiled().dsl().kind() == ReportDsl.Kind.PIVOT) {
            var projection = runtime.pivotProjection(access);
            runtime.spreadsheetExporter(access, temporaryBudget)
                    .data(
                            projection.dsl(),
                            consumer -> projection.data().rows().forEach(consumer),
                            output,
                            cancellation);
            return;
        }
        if (job.format().equals("XLSX_DATA")) {
            runtime.spreadsheetExporter(access, temporaryBudget)
                    .data(
                            access.binding().compiled().dsl(),
                            consumer -> runtime.each(access, consumer, cancellation),
                            output,
                            cancellation);
            return;
        }
        if (access.run().rows() > 5000) throw ReportFault.capacity();
        var plan = runtime.layout(access, cancellation);
        cancellation.check();
        if (job.format().equals("PDF"))
            runtime.withLayoutFont(
                    access,
                    cancellation,
                    font -> new ReportPdfExporter().write(plan, output, font, cancellation));
        else if (job.format().equals("XLSX_LAYOUT"))
            new ReportSpreadsheetExporter().layout(plan, output, cancellation);
        else throw ReportFault.invalid("导出格式不支持");
    }
}
