package com.nexus.erp.fin.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.report.domain.bo.ClosingRequestBo;
import com.nexus.erp.fin.report.domain.bo.ClosingYearlyBo;
import com.nexus.erp.fin.report.domain.vo.ClosingStepVo;
import com.nexus.erp.fin.report.domain.vo.FinCloseLogVo;
import com.nexus.erp.fin.report.service.IFinClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 期末关账 Controller
 *
 * @author nexus
 * @date 2026-07-31
 */
@Validated
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/erp/fin/closing")
public class FinClosingController extends BaseController {

    private final IFinClosingService finClosingService;

    /** 月结向导步骤状态 */
    @SaCheckPermission("fin:closing:list")
    @GetMapping("/steps")
    public R<ClosingStepVo> steps(@RequestParam Long periodId) {
        return R.ok(finClosingService.steps(periodId));
    }

    /** 试算校验 */
    @SaCheckPermission("fin:closing:list")
    @PostMapping("/trialCheck")
    public R<Void> trialCheck(@Validated @RequestBody ClosingRequestBo bo) {
        finClosingService.trialCheck(bo.getPeriodId());
        return toAjax(1);
    }

    /** 损益结转(幂等) */
    @SaCheckPermission("fin:closing:carry")
    @Log(title = "期末损益结转", businessType = BusinessType.UPDATE)
    @PostMapping("/carryPnl")
    public R<Void> carryPnl(@Validated @RequestBody ClosingRequestBo bo) {
        finClosingService.carryPnl(bo.getPeriodId());
        return toAjax(1);
    }

    /** 外币期末重估(幂等) */
    @SaCheckPermission("fin:closing:reval")
    @Log(title = "外币期末重估", businessType = BusinessType.UPDATE)
    @PostMapping("/revaluate")
    public R<Void> revaluate(@Validated @RequestBody ClosingRequestBo bo) {
        finClosingService.revaluate(bo.getPeriodId());
        return toAjax(1);
    }

    /** 月结整体执行 */
    @SaCheckPermission("fin:closing:close")
    @Log(title = "期末月结", businessType = BusinessType.UPDATE)
    @PostMapping("/close")
    public R<Void> monthlyClosing(@Validated @RequestBody ClosingRequestBo bo) {
        finClosingService.monthlyClosing(bo.getPeriodId());
        return toAjax(1);
    }

    /** 年结 */
    @SaCheckPermission("fin:closing:yearly")
    @Log(title = "期末年结", businessType = BusinessType.UPDATE)
    @PostMapping("/yearly")
    public R<Void> yearlyClosing(@Validated @RequestBody ClosingYearlyBo bo) {
        finClosingService.yearlyClosing(bo.getFiscalYear());
        return toAjax(1);
    }

    /** 关账日志 */
    @SaCheckPermission("fin:closing:list")
    @GetMapping("/logs")
    public R<List<FinCloseLogVo>> logs(@RequestParam Long periodId) {
        return R.ok(finClosingService.closeLogs(periodId));
    }
}
