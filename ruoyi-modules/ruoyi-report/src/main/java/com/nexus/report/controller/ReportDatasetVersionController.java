package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.service.*;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/report")
public class ReportDatasetVersionController {
    private final ReportRequestActor actors;
    private final ReportDatasetVersionService versions;
    private final ReportDefinitionService definitions;

    public ReportDatasetVersionController(
            ReportRequestActor actors,
            ReportDatasetVersionService versions,
            ReportDefinitionService definitions) {
        this.actors = actors;
        this.versions = versions;
        this.definitions = definitions;
    }

    @SaCheckPermission("report:dataset:list")
    @GetMapping("/datasets/versions/{id}")
    public R<ReportDatasetVersionService.View> version(@PathVariable String id) {
        return R.ok(versions.read(actors.current(), id));
    }

    @SaCheckPermission("report:dataset:list")
    @GetMapping("/datasets/versions/{id}/diff")
    public R<ReportDatasetVersionService.Diff> diff(
            @PathVariable String id, @RequestParam String target) {
        return R.ok(versions.diff(actors.current(), id, target));
    }

    @SaCheckPermission("report:definition:list")
    @GetMapping("/engines")
    public R<java.util.List<com.nexus.report.version.ReportEngineRegistry.Capability>> engines() {
        return R.ok(definitions.capabilities(actors.current()));
    }
}
