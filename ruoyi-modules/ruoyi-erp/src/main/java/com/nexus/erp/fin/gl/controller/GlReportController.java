package com.nexus.erp.fin.gl.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.gl.domain.vo.TrialBalanceVo;
import com.nexus.erp.fin.gl.service.IGlAccountBalanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GL 财务报表 Controller
 *
 * @author nexus
 * @date 2026-07-29
 */
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/erp/fin/gl/report")
public class GlReportController extends BaseController {

    private final IGlAccountBalanceService glAccountBalanceService;

    /** 试算平衡表 */
    @SaCheckPermission("fin:gl:report:list")
    @GetMapping("/trialBalance")
    public R<List<TrialBalanceVo>> trialBalance(
            @RequestParam Long periodId,
            @RequestParam(required = false) String accountType) {
        return R.ok(glAccountBalanceService.trialBalance(periodId, accountType));
    }
}
