package com.nexus.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.web.domain.vo.PdaTraceVo;
import com.nexus.web.service.PdaTraceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 统一追溯查询接口 (M3 批次4; 契约见 nexus-pda/doc/plans/M3-执行计划.md 批次 4)
 * <p>
 * 一次输入(批次号/SN/物料编码/MES 批次号)聚合三套既有追溯能力:
 * ERP 库存流水正反向追溯 + MES 批次链。erp/mes 模块互不依赖, 门面置于聚合模块 ruoyi-admin。
 * 权限: 具备任一构成权限点即可(wm:trace:query 或 mes:lt:lot:trace)。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/trace")
public class AppPdaTraceController {

    private final PdaTraceService pdaTraceService;

    /**
     * 统一追溯查询
     *
     * @param keyword 批次号 / SN / 物料编码 / MES 批次号
     * @return 解析结果 + ERP 正反向追溯树 + MES 批次链 (未命中维度为 null)
     */
    @SaCheckPermission(value = {"wm:trace:query", "mes:lt:lot:trace"}, mode = SaMode.OR)
    @Log(title = "PDA追溯查询", businessType = BusinessType.OTHER)
    @GetMapping
    public R<PdaTraceVo> trace(@RequestParam @NotBlank(message = "追溯关键字不能为空") String keyword) {
        return R.ok(pdaTraceService.trace(keyword.trim()));
    }
}
