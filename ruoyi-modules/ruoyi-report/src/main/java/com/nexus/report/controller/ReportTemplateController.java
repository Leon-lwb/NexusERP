package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.domain.ReportRecords.Definition;
import com.nexus.report.service.ReportTemplatePackageService;

import org.springframework.web.bind.annotation.*;

/** 模板包与复制入口，校验源编辑权限、目标创建权限和固定依赖。 */
@RestController
@RequestMapping("/report/templates")
public class ReportTemplateController {
    /** 保存当前 {@code actors} 值。 */
    private final ReportRequestActor actors;

    /** 保存当前 {@code templates} 值。 */
    private final ReportTemplatePackageService templates;

    /**
     * 初始化报表模板接口并保存所需依赖或不可变值。
     *
     * @param actors 当前操作的 {@code actors} 输入
     * @param templates 当前操作的 {@code templates} 输入
     */
    public ReportTemplateController(
            ReportRequestActor actors, ReportTemplatePackageService templates) {
        this.actors = actors;
        this.templates = templates;
    }

    /**
     * Import请求，负责当前模块对应的契约或处理职责。
     *
     * @param templatePackage 当前契约的templatePackage配置或状态
     * @param datasetVersionId 当前契约的datasetVersionId配置或状态
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param catalogId 当前契约的catalogId配置或状态
     */
    public record ImportRequest(
            ReportTemplatePackageService.TemplatePackage templatePackage,
            String datasetVersionId,
            String code,
            String title,
            String catalogId) {}

    /**
     * Copy请求，负责当前模块对应的契约或处理职责。
     *
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param catalogId 当前契约的catalogId配置或状态
     */
    public record CopyRequest(String code, String title, String catalogId) {}

    /**
     * 导出不包含租户授权和运行数据的模板包。
     *
     * @param id 资源标识
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:edit")
    @GetMapping("/{id}/package")
    public R<ReportTemplatePackageService.TemplatePackage> export(@PathVariable String id) {
        return R.ok(templates.export(actors.current(), id));
    }

    /**
     * 校验依赖和包完整性后创建本租户草稿。
     *
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:add")
    @PostMapping("/import")
    public R<Definition> importDraft(@RequestBody ImportRequest body) {
        return R.ok(
                templates.importDraft(
                        actors.current(),
                        body.templatePackage(),
                        body.datasetVersionId(),
                        body.code(),
                        body.title(),
                        body.catalogId()));
    }

    /**
     * 复制已授权定义并清除业务标识默认值。
     *
     * @param id 资源标识
     * @param body 当前操作的 {@code body} 输入
     * @return 当前处理返回的固定结果
     */
    @SaCheckPermission("report:definition:add")
    @PostMapping("/{id}/copy")
    public R<Definition> copy(@PathVariable String id, @RequestBody CopyRequest body) {
        return R.ok(
                templates.copy(actors.current(), id, body.code(), body.title(), body.catalogId()));
    }
}
