package com.nexus.erp.wm.controller.app;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.annotation.PdaIdempotent;
import com.nexus.erp.wm.domain.bo.pda.PdaReturnConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaReturnDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.service.IPdaReturnService;
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
 * PDA 退料接口 (PC 建单, PDA 扫码核销; 良品/不良品/报废三类)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/return")
public class AppPdaReturnController {

    private final IPdaReturnService pdaReturnService;

    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaReturnService.todo(keyword, pageQuery);
    }

    @GetMapping("/{returnId}")
    public R<PdaReturnDetailVo> detail(@PathVariable Long returnId) {
        return R.ok(pdaReturnService.detail(returnId));
    }

    /** 行级扫码核销 (幂等键: 单号:行号:confirm) */
    @PdaIdempotent
    @Log(title = "PDA退料核销", businessType = BusinessType.UPDATE)
    @PostMapping("/{returnId}/line/confirm")
    public R<PdaActionResultVo> confirmLine(
        @PathVariable Long returnId,
        @Validated @RequestBody @NotNull PdaReturnConfirmBo bo) {
        return R.ok(pdaReturnService.confirmLine(returnId, bo));
    }

    /** 退料完成过账 (幂等键: 单号:0:finish) */
    @PdaIdempotent
    @Log(title = "PDA退料完成", businessType = BusinessType.UPDATE)
    @PostMapping("/{returnId}/finish")
    public R<PdaActionResultVo> finish(@PathVariable Long returnId) {
        return R.ok(pdaReturnService.finish(returnId));
    }
}
