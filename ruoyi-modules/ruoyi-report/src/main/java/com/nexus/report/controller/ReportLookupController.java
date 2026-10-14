package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.report.*;
import com.nexus.report.service.ReportLookupService;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/report/lookups")
@SaCheckPermission("report:runtime:preview")
public class ReportLookupController {
    private final ReportRequestActor actors;
    private final ReportLookupService lookups;

    public ReportLookupController(ReportRequestActor actors, ReportLookupService lookups) {
        this.actors = actors;
        this.lookups = lookups;
    }

    @GetMapping("/{version}")
    public R<List<ReportLookupBinding>> bindings(@PathVariable String version) {
        return R.ok(lookups.bindings(actors.current(), version));
    }

    @GetMapping("/{version}/{parameter}")
    public R<ReportLookupProvider.Result> query(
            @PathVariable String version,
            @PathVariable String parameter,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(
                lookups.query(
                        actors.current(),
                        version,
                        parameter,
                        new ReportLookupProvider.Query(keyword, page, size)));
    }
}
