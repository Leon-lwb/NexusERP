package com.nexus.erp.fin.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.report.domain.bo.ReportItemBo;
import com.nexus.erp.fin.report.domain.vo.ReportItemVo;
import com.nexus.erp.fin.report.domain.vo.ReportLineVo;
import com.nexus.erp.fin.report.service.IReportDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 财务报表 Controller
 *
 * @author nexus
 * @date 2026-07-31
 */
@Validated
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/erp/fin/report")
public class FinReportController extends BaseController {

    private final IReportDataService reportDataService;

    /** 资产负债表 */
    @SaCheckPermission("fin:report:list")
    @GetMapping("/balance")
    public R<List<ReportLineVo>> balance(@RequestParam Long periodId,
                                         @RequestParam(defaultValue = "false") Boolean includeZero) {
        return R.ok(reportDataService.balanceSheet(periodId, includeZero));
    }

    /** 利润表 */
    @SaCheckPermission("fin:report:list")
    @GetMapping("/income")
    public R<List<ReportLineVo>> income(@RequestParam Long periodId,
                                        @RequestParam(defaultValue = "false") Boolean yearToDate) {
        return R.ok(reportDataService.incomeStatement(periodId, yearToDate));
    }

    /** 现金流量表 */
    @SaCheckPermission("fin:report:list")
    @GetMapping("/cashFlow")
    public R<List<ReportLineVo>> cashFlow(@RequestParam Long periodId,
                                          @RequestParam(defaultValue = "false") Boolean yearToDate) {
        return R.ok(reportDataService.cashFlow(periodId, yearToDate));
    }

    /** 报表项目映射配置查询 */
    @SaCheckPermission("fin:report:list")
    @GetMapping("/itemConfig")
    public R<List<ReportItemVo>> itemConfig(@RequestParam(required = false) String reportType) {
        return R.ok(reportDataService.itemConfig(reportType));
    }

    /** 报表项目映射保存(新增/修改) */
    @SaCheckPermission("fin:report:item:edit")
    @PostMapping("/itemConfig")
    public R<Void> saveItemConfig(@Validated @RequestBody ReportItemBo bo) {
        return toAjax(reportDataService.saveItemConfig(bo) ? 1 : 0);
    }

    /** 报表项目映射删除 */
    @SaCheckPermission("fin:report:item:edit")
    @DeleteMapping("/itemConfig/{itemIds}")
    public R<Void> removeItemConfig(@PathVariable Long[] itemIds) {
        return toAjax(reportDataService.deleteItemConfig(itemIds) ? 1 : 0);
    }
}
