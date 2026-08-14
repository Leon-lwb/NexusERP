package com.nexus.erp.wm.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

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
@RequestMapping("/erp/wm/report")
public class WmInventoryReportController {

    private final WmInventoryReportServiceImpl reportService;

    /**
     * 库存汇总报表（按物料分类维度）。
     * <p>统计各物料分类下的物料种类数、库存总量与库存金额，支持按仓库与分类筛选。</p>
     *
     * @param warehouseId 仓库ID，为空时统计全部仓库
     * @param categoryCode 物料分类编码，为空时统计全部分类
     * @return 各分类维度的库存汇总数据列表
     */
    @SaCheckPermission("wm:materialStock:list")
    @GetMapping("/summary")
    public R<List<Map<String, Object>>> inventorySummary(
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) String categoryCode) {
        return R.ok(reportService.getInventorySummary(warehouseId, categoryCode));
    }

    /**
     * 库龄分析报表。
     * <p>按库存创建时间划分 0-30/31-60/61-90/91-180/180天以上 区间，统计各区间的SKU数与库存量。</p>
     *
     * @param warehouseId 仓库ID，为空时统计全部仓库
     * @param daysThreshold 库龄阈值（天），为空时默认 90 天
     * @return 各库龄区间的库存分析数据列表
     */
    @SaCheckPermission("wm:materialStock:list")
    @GetMapping("/aging")
    public R<List<Map<String, Object>>> inventoryAging(
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) Integer daysThreshold) {
        return R.ok(reportService.getInventoryAging(warehouseId, daysThreshold));
    }

    /**
     * 库存周转率报表。
     * <p>按月份与物料分类统计出库数量与平均库存，计算库存周转率。</p>
     *
     * @param categoryCode 物料分类编码，为空时统计全部分类
     * @param months 统计月份数，为空时默认 6 个月
     * @return 各月份与分类维度的周转率数据列表
     */
    @SaCheckPermission("wm:materialStock:list")
    @GetMapping("/turnover")
    public R<List<Map<String, Object>>> inventoryTurnover(
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) Integer months) {
        return R.ok(reportService.getInventoryTurnover(categoryCode, months));
    }
}
