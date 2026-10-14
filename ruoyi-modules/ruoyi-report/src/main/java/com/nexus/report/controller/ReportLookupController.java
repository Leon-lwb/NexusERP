package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.report.*;
import com.nexus.report.service.ReportLookupService;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 固定契约参数的候选接口，不接收表名、SQL或身份。 */
@RestController
@RequestMapping("/report/lookups")
@SaCheckPermission("report:runtime:preview")
public class ReportLookupController {
    /** 保存当前 {@code actors} 值。 */
    private final ReportRequestActor actors;

    /** 保存当前 {@code lookups} 值。 */
    private final ReportLookupService lookups;

    /**
     * 初始化报表选择器接口并保存所需依赖或不可变值。
     *
     * @param actors 当前操作的 {@code actors} 输入
     * @param lookups 当前操作的 {@code lookups} 输入
     */
    public ReportLookupController(ReportRequestActor actors, ReportLookupService lookups) {
        this.actors = actors;
        this.lookups = lookups;
    }

    /**
     * 列出固定契约已安装且有权限的选择器。
     *
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    @GetMapping("/{version}")
    public R<List<ReportLookupBinding>> bindings(@PathVariable String version) {
        return R.ok(lookups.bindings(actors.current(), version));
    }

    /**
     * 执行受控只读查询。
     *
     * @param version 固定版本
     * @param parameter 当前操作的 {@code parameter} 输入
     * @param keyword 当前操作的 {@code keyword} 输入
     * @param page 当前页
     * @param size 有界页大小
     * @return 当前处理返回的固定结果
     */
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
