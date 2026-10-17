package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;

import com.nexus.common.core.domain.R;
import com.nexus.report.batch.ReportBatchService;
import com.nexus.report.batch.ReportBatchPdfService;
import com.nexus.report.extension.ReportResourceStore;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/** 批量单据固定版本并独立入队，结果从正常导出中心领取。 */
@RestController
@RequestMapping("/report/batches")
public final class ReportBatchController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前service状态或配置。 */
    private final ReportBatchService service;

    /** 已存在私有PDF的同步合并及一次性交付入口。 */
    private final ReportBatchPdfService pdfs;

    /**
     * 复用正常身份和批次服务。
     *
     * @param actors 当前契约的actors配置或状态
     * @param service 当前契约的service配置或状态
     * @param pdfs 私有批次PDF合并及交付服务
     */
    public ReportBatchController(ReportRequestActor actors, ReportBatchService service, ReportBatchPdfService pdfs) {
        this.actors = actors;
        this.service = service;
        this.pdfs = pdfs;
    }

    /**
     * 批次草稿输入只包含编码、标题和固定单据列表。
     *
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param documents 当前契约的documents配置或状态
     */
    public record Request(String code, String title, List<ReportBatchService.Document> documents) {}

    /**
     * 查询可访问批次。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:batch:list")
    @GetMapping
    public R<List<ReportResourceStore.Draft>> list() {
        return R.ok(service.list(actors.current()));
    }

    /**
     * 显式创建不可变批次。
     *
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:batch:manage")
    @PostMapping
    public R<ReportResourceStore.Draft> create(@RequestBody Request body) {
        return R.ok(service.create(actors.current(), body.code(), body.title(), body.documents()));
    }

    /**
     * 重复提交仍只产生每单据一个有效任务。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:batch:manage")
    @PostMapping("/{id}/start")
    public R<Void> start(@PathVariable String id) {
        service.start(actors.current(), id);
        return R.ok();
    }

    /**
     * 返回已重新鉴权的逐单据状态。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:batch:list")
    @GetMapping("/{id}/items")
    public R<List<ReportBatchService.Item>> items(@PathVariable String id) {
        return R.ok(service.items(actors.current(), id));
    }

    /**
     * 仅允许创建者领取完整合并PDF；发送响应头前及流中持续重新鉴权。
     *
     * @param id 已上线批次标识
     * @param response 当前请求的私有文件响应
     */
    @SaCheckPermission(value = {"report:batch:list", "report:runtime:preview", "report:runtime:export"}, mode = SaMode.AND)
    @GetMapping("/{id}/pdf")
    public void pdf(@PathVariable String id, HttpServletResponse response) throws IOException {
        try (var prepared = pdfs.merge(actors.current(), id)) {
            prepared.verify();
            response.setContentType("application/pdf");
            response.setHeader("Cache-Control", "no-store, private");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", ContentDisposition.attachment()
                    .filename("nexus-report-batch-" + id + ".pdf").build().toString());
            response.setContentLengthLong(prepared.bytes());
            prepared.write(response.getOutputStream());
        }
    }
}
