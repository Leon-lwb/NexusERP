package com.nexus.mes.pda.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.annotation.PdaIdempotent;
import com.nexus.mes.pda.domain.bo.PdaReportCompleteBo;
import com.nexus.mes.pda.domain.vo.PdaActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaDispatchItemVo;
import com.nexus.mes.pda.service.IPdaReportService;
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
 * PDA 扫码报工接口 (派工单驱动: 开工 → 完工数量申报)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/report")
public class AppPdaReportController {

    private final IPdaReportService pdaReportService;

    /** 待报工派工列表 */
    @GetMapping("/todo")
    public TableDataInfo<PdaDispatchItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaReportService.todo(keyword, pageQuery);
    }

    /** 派工详情 */
    @GetMapping("/{dispatchId}")
    public R<PdaDispatchItemVo> detail(@PathVariable Long dispatchId) {
        return R.ok(pdaReportService.detail(dispatchId));
    }

    /** 开工 (幂等键: 派工单号:0:start) */
    @PdaIdempotent
    @Log(title = "PDA报工开工", businessType = BusinessType.UPDATE)
    @PostMapping("/{dispatchId}/start")
    public R<PdaActionResultVo> start(@PathVariable Long dispatchId) {
        return R.ok(pdaReportService.start(dispatchId));
    }

    /** 完工报工 (幂等键: 派工单号:0:complete) */
    @PdaIdempotent
    @Log(title = "PDA完工报工", businessType = BusinessType.UPDATE)
    @PostMapping("/{dispatchId}/complete")
    public R<PdaActionResultVo> complete(
        @PathVariable Long dispatchId,
        @Validated @RequestBody @NotNull PdaReportCompleteBo bo) {
        return R.ok(pdaReportService.complete(dispatchId, bo));
    }
}
