package com.nexus.erp.fin.arap.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.arap.domain.vo.OpenItemVo;
import com.nexus.erp.fin.arap.service.IFinSettlementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应收应付报表 Controller(未清项明细)
 *
 * @author nexus
 * @date 2026-07-30
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/arap/report")
public class ArapReportController extends BaseController {

    private final IFinSettlementService finSettlementService;

    /** 查询未清项明细(应收应付报表) */
    @SaCheckPermission("fin:arap:report:list")
    @GetMapping("/openItems")
    public R<List<OpenItemVo>> openItems(@RequestParam String direction,
                                         @RequestParam(required = false) Long partnerId) {
        return R.ok(finSettlementService.queryOpenItems(direction, partnerId));
    }
}
