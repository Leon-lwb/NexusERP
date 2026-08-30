package com.nexus.mes.pda.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.nexus.common.core.domain.R;
import com.nexus.mes.pda.domain.vo.PdaAqlPlanVo;
import com.nexus.mes.pda.util.AqlSampler;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA AQL 抽样试算接口 (M3 批次 5; GB/T 2828.1 简化版)
 * <p>
 * 决策辅助: 按 批量/检验水平/AQL 返回正常检验一次抽样方案(字码/样本量/Ac/Re),
 * 传 defectQty 时附判定建议;最终判定以检验员提交的 inspectResult 为准。
 * 三检共用,权限取三检列表权限之 OR。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/qc/aql")
public class AppPdaQcAqlController {

    /** 试算抽样方案 (lotSize≥2, level 默认 II) */
    @SaCheckPermission(value = {"mes:qc:iqc:list", "mes:qc:ipqc:list", "mes:qc:oqc:list"}, mode = SaMode.OR)
    @GetMapping("/plan")
    public R<PdaAqlPlanVo> plan(@RequestParam long lotSize,
                                @RequestParam double aql,
                                @RequestParam(required = false) Long defectQty,
                                @RequestParam(defaultValue = "II") String level) {
        if (!AqlSampler.isSupportedAql(aql)) {
            return R.fail("AQL 超出简化抽样表支持范围(0.025~10)");
        }
        try {
            AqlSampler.AqlPlan plan = AqlSampler.plan(lotSize, level, aql);
            return R.ok(PdaAqlPlanVo.of(lotSize, level, aql, plan, defectQty));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}
