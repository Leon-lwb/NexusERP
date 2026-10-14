package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.domain.ReportRecords.Definition;
import com.nexus.report.service.ReportTemplatePackageService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/report/templates")
public class ReportTemplateController {
    private final ReportRequestActor actors;
    private final ReportTemplatePackageService templates;

    public ReportTemplateController(
            ReportRequestActor actors, ReportTemplatePackageService templates) {
        this.actors = actors;
        this.templates = templates;
    }

    public record ImportRequest(
            ReportTemplatePackageService.TemplatePackage templatePackage,
            String datasetVersionId,
            String code,
            String title,
            String catalogId) {}

    public record CopyRequest(String code, String title, String catalogId) {}

    @SaCheckPermission("report:definition:edit")
    @GetMapping("/{id}/package")
    public R<ReportTemplatePackageService.TemplatePackage> export(@PathVariable String id) {
        return R.ok(templates.export(actors.current(), id));
    }

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

    @SaCheckPermission("report:definition:add")
    @PostMapping("/{id}/copy")
    public R<Definition> copy(@PathVariable String id, @RequestBody CopyRequest body) {
        return R.ok(
                templates.copy(actors.current(), id, body.code(), body.title(), body.catalogId()));
    }
}
