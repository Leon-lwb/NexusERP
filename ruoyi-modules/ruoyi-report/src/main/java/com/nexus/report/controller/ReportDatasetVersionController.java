package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.service.*;
import com.nexus.report.version.ReportEngineRegistry;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 读取授权历史契约和升级差异，不自动替换草稿或已发布版本。 */
@RestController
@RequestMapping("/report")
public class ReportDatasetVersionController {
    /** 保存当前 {@code actors} 值。 */
    private final ReportRequestActor actors;

    /** 保存当前 {@code versions} 值。 */
    private final ReportDatasetVersionService versions;

    /** 保存当前 {@code definitions} 值。 */
    private final ReportDefinitionService definitions;

    /**
     * 初始化报表数据集版本接口并保存所需依赖或不可变值。
     *
     * @param actors 当前操作的 {@code actors} 输入
     * @param versions 当前操作的 {@code versions} 输入
     * @param definitions 当前操作的 {@code definitions} 输入
     */
    public ReportDatasetVersionController(
            ReportRequestActor actors,
            ReportDatasetVersionService versions,
            ReportDefinitionService definitions) {
        this.actors = actors;
        this.versions = versions;
        this.definitions = definitions;
    }

    /**
     * 完成 {@code version} 对应的报表数据集版本接口处理。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:list")
    @GetMapping("/datasets/versions/{id}")
    public R<ReportDatasetVersionService.View> version(@PathVariable String id) {
        return R.ok(versions.read(actors.current(), id));
    }

    /**
     * 比较两份已授权历史契约并复核撤权。
     *
     * @param id 资源标识
     * @param target 目标定义或资源
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:dataset:list")
    @GetMapping("/datasets/versions/{id}/diff")
    public R<ReportDatasetVersionService.Diff> diff(
            @PathVariable String id, @RequestParam String target) {
        return R.ok(versions.diff(actors.current(), id, target));
    }

    /**
     * 完成 {@code engines} 对应的报表数据集版本接口处理。
     *
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:list")
    @GetMapping("/engines")
    public R<List<ReportEngineRegistry.Capability>> engines() {
        return R.ok(definitions.capabilities(actors.current()));
    }
}
