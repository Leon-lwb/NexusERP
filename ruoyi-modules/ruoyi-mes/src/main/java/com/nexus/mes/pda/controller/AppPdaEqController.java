package com.nexus.mes.pda.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.pda.annotation.PdaIdempotent;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaEqInspectionVo;
import com.nexus.mes.pda.domain.vo.PdaEqItemVo;
import com.nexus.mes.pda.service.IPdaEqService;
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
 * PDA 设备点检接口 (登录态; 提交不合格自动置设备 FAULT + 触发安灯)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/eq")
public class AppPdaEqController {

    private final IPdaEqService pdaEqService;

    /** 设备列表 (扫码/名称定位) */
    @SaCheckPermission("mes:eq:equipment:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaEqItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaEqService.todo(keyword, pageQuery);
    }

    /** 点检详情: 点检项模板 + 上次结果 */
    @SaCheckPermission("mes:eq:equipment:list")
    @GetMapping("/{equipmentId}/inspection")
    public R<PdaEqInspectionVo> detail(@PathVariable Long equipmentId) {
        return R.ok(pdaEqService.detail(equipmentId));
    }

    /** 提交点检 (幂等键: 设备码:0:inspect) */
    @SaCheckPermission("mes:eq:equipment:inspect")
    @PdaIdempotent
    @Log(title = "PDA设备点检", businessType = BusinessType.UPDATE)
    @PostMapping("/{equipmentId}/inspection/submit")
    public R<MesActionResultVo> submit(
        @PathVariable Long equipmentId,
        @Validated @RequestBody @NotNull InspectionResultBo bo) {
        return R.ok(pdaEqService.submit(equipmentId, bo));
    }
}
