package com.nexus.mes.pda.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.pda.PdaIdempotent;
import com.nexus.mes.pda.domain.bo.PdaMaintExecuteBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaEqMaintDetailVo;
import com.nexus.mes.pda.domain.vo.PdaEqMaintItemVo;
import com.nexus.mes.pda.service.IPdaEqMaintService;
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
 * PDA 保养维保接口
 * <p>
 * 到期保养计划(ACTIVE 且 next_date<=今天)在 PDA 查看并执行: 落保养记录 + 推进下次保养日期。
 * 计划维护仍在 PC 端(设备管理-保养计划)。幂等键约定: 设备码:0:maint:nonce。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/eq/maint")
public class AppPdaEqMaintController {

    private final IPdaEqMaintService pdaEqMaintService;

    /** 到期待办 (keyword 扫设备编码/名称或计划名) */
    @SaCheckPermission("mes:eq:maintenance:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaEqMaintItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaEqMaintService.todo(keyword, pageQuery);
    }

    /** 保养计划详情 (计划+设备+最近保养记录) */
    @SaCheckPermission("mes:eq:maintenance:list")
    @GetMapping("/{planId}")
    public R<PdaEqMaintDetailVo> detail(@PathVariable Long planId) {
        return R.ok(pdaEqMaintService.detail(planId));
    }

    /** 执行保养 (幂等; 落记录+推进下次保养日期) */
    @SaCheckPermission("mes:eq:maintenance:complete")
    @PdaIdempotent
    @Log(title = "PDA 保养执行", businessType = BusinessType.INSERT)
    @PostMapping("/{planId}/execute")
    public R<MesActionResultVo> execute(@PathVariable Long planId,
                                        @Validated @RequestBody @NotNull PdaMaintExecuteBo bo) {
        return R.ok(pdaEqMaintService.execute(planId, bo));
    }
}
