package com.nexus.erp.wm.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.pda.PdaIdempotent;
import com.nexus.erp.wm.domain.bo.pda.PdaHandoverConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaHandoverDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.service.IPdaHandoverService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 上料交接作业接口 (M3 批次2; 契约见 doc/plans/2026-08-30-M3-执行计划.md 批次 2)
 * <p>
 * 流程: PC 建单(DRAFT) → 材料员行级核销(累计实交) → 产线操作工签收(过账 源库位出→线边库位入)。
 * 幂等键约定: 核销 单号:行号:confirm(带每次操作 nonce); 签收 单号:0:sign。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/handover")
public class AppPdaHandoverController {

    private final IPdaHandoverService pdaHandoverService;

    /** 交接单列表 (扫单号/工单号定位) */
    @SaCheckPermission("wm:materialHandover:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaHandoverService.todo(keyword, pageQuery);
    }

    /** 交接单详情 (头+行) */
    @SaCheckPermission("wm:materialHandover:list")
    @GetMapping("/{handoverId}")
    public R<PdaHandoverDetailVo> detail(@PathVariable Long handoverId) {
        return R.ok(pdaHandoverService.detail(handoverId));
    }

    /** 行级扫码核销 (幂等键: 单号:行号:confirm) */
    @SaCheckPermission("wm:materialHandover:edit")
    @PdaIdempotent
    @Log(title = "PDA交接核销", businessType = BusinessType.UPDATE)
    @PostMapping("/{handoverId}/line/confirm")
    public R<PdaActionResultVo> confirmLine(@PathVariable Long handoverId,
                                            @Validated @RequestBody @NotNull PdaHandoverConfirmBo bo) {
        return R.ok(pdaHandoverService.confirmLine(handoverId, bo));
    }

    /** 签收过账 (幂等键: 单号:0:sign) */
    @SaCheckPermission("wm:materialHandover:edit")
    @PdaIdempotent
    @Log(title = "PDA交接签收", businessType = BusinessType.UPDATE)
    @PostMapping("/{handoverId}/sign")
    public R<PdaActionResultVo> sign(@PathVariable Long handoverId) {
        return R.ok(pdaHandoverService.sign(handoverId));
    }
}
