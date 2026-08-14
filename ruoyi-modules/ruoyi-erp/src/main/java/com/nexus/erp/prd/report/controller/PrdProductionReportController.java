package com.nexus.erp.prd.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.erp.prd.report.service.PrdProductionReportServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生产报表 Controller
 * <p>提供生产执行相关的统计报表查询接口，包括工单完工率、生产效率等。属于 PRD（生产计划）模块。</p>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/report")
public class PrdProductionReportController {

    private final PrdProductionReportServiceImpl reportService;

    /**
     * 查询工单完工率报表
     * <p>权限标识：{@code prd:workOrder:list}</p>
     *
     * @param months 统计月份数，为空时默认 6 个月
     * @return 各月工单总数、完工数与完工率
     */
    @SaCheckPermission("prd:workOrder:list")
    @GetMapping("/completionRate")
    public R<List<Map<String, Object>>> workOrderCompletionRate(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getWorkOrderCompletionRate(months));
    }

    /**
     * 查询生产效率报表
     * <p>权限标识：{@code prd:workOrder:list}</p>
     *
     * @param months 统计月份数，为空时默认 6 个月
     * @return 各月计划产量、完工产量与效率百分比
     */
    @SaCheckPermission("prd:workOrder:list")
    @GetMapping("/efficiency")
    public R<List<Map<String, Object>>> productionEfficiency(
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getProductionEfficiency(months));
    }
}
