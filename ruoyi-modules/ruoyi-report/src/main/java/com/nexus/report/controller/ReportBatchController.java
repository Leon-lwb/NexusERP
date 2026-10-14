package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.batch.ReportBatchService;
import com.nexus.report.extension.ReportResourceStore;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 批量单据固定版本并独立入队，结果从正常导出中心领取。 */
@RestController
@RequestMapping("/report/batches")
public final class ReportBatchController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前service状态或配置。 */
    private final ReportBatchService service;

    /**
     * 复用正常身份和批次服务。
     *
     * @param actors 当前契约的actors配置或状态
     * @param service 当前契约的service配置或状态
     */
    public ReportBatchController(ReportRequestActor actors, ReportBatchService service) {
        this.actors = actors;
        this.service = service;
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
}
