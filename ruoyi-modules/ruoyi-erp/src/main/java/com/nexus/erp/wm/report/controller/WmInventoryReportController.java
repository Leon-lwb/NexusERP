package com.nexus.erp.wm.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.report.service.WmInventoryReportServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库存业务报表
 *
 * @author nexus
 * @date 2026-06-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@DS("erp")
@RequestMapping("/erp/wm/report")
public class WmInventoryReportController {

    private final WmInventoryReportServiceImpl reportService;

    /**
     * 库存汇总报表（按物料分类维度）
     */
    @SaCheckPermission("wm:materialStock:list")
    @GetMapping("/summary")
    public R<List<Map<String, Object>>> inventorySummary(
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) String categoryCode) {
        return R.ok(reportService.getInventorySummary(warehouseId, categoryCode));
    }

    /**
     * 库龄分析报表
     */
    @SaCheckPermission("wm:materialStock:list")
    @GetMapping("/aging")
    public R<List<Map<String, Object>>> inventoryAging(
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) Integer daysThreshold) {
        return R.ok(reportService.getInventoryAging(warehouseId, daysThreshold));
    }

    /**
     * 库存周转率报表
     */
    @SaCheckPermission("wm:materialStock:list")
    @GetMapping("/turnover")
    public R<List<Map<String, Object>>> inventoryTurnover(
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getInventoryTurnover(categoryCode, months));
    }
}
