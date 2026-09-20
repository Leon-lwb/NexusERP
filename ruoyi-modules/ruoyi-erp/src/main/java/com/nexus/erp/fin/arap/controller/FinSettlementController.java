package com.nexus.erp.fin.arap.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.arap.domain.bo.FinSettlementBo;
import com.nexus.erp.fin.arap.domain.vo.FinSettlementVo;
import com.nexus.erp.fin.arap.domain.vo.OpenItemVo;
import com.nexus.erp.fin.arap.service.IFinSettlementService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应收应付核销 Controller
 *
 * @author nexus
 * @date 2026-07-30
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/arap/settlement")
public class FinSettlementController extends BaseController {

    private final IFinSettlementService finSettlementService;

    /** 分页查询核销单据 */
    @SaCheckPermission("fin:arap:settlement:list")
    @GetMapping("/list")
    public TableDataInfo<FinSettlementVo> list(FinSettlementBo bo, PageQuery pageQuery) {
        return finSettlementService.queryPageList(bo, pageQuery);
    }

    /** 执行核销(应收/应付) */
    @SaCheckPermission("fin:arap:settlement:settle")
    @Log(title = "应收应付核销", businessType = BusinessType.INSERT)
    @PostMapping("/settle")
    public R<Void> settle(@Validated @RequestBody FinSettlementBo bo) {
        finSettlementService.settle(bo);
        return toAjax(1);
    }

    /** 反核销(撤销一次核销:回退发票已核销额/收付款单已结算额与状态/未清项清账标志) */
    @SaCheckPermission("fin:arap:settlement:unsettle")
    @Log(title = "应收应付反核销", businessType = BusinessType.UPDATE)
    @PostMapping("/unsettle/{settlementId}")
    public R<Void> unsettle(@PathVariable Long settlementId) {
        finSettlementService.unsettle(settlementId);
        return toAjax(1);
    }

    /** 未清项查询(核销页选未清发票/收付款单) */
    @SaCheckPermission("fin:arap:settlement:query")
    @GetMapping("/openItems")
    public R<List<OpenItemVo>> openItems(@RequestParam String direction,
                                         @RequestParam(required = false) Long partnerId) {
        return R.ok(finSettlementService.queryOpenItems(direction, partnerId));
    }
}
