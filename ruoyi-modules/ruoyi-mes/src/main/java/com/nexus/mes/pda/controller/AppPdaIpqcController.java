package com.nexus.mes.pda.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.annotation.PdaIdempotent;
import com.nexus.mes.pda.domain.bo.PdaQcInspectBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaQcDetailVo;
import com.nexus.mes.pda.domain.vo.PdaQcItemVo;
import com.nexus.mes.pda.service.IPdaQcService;
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
 * PDA IPQC 巡检接口 (M3 批次3; 契约见 nexus-pda doc/plans/2026-08-30-M3-执行计划.md 批次 3)
 * <p>
 * 提交委托既有 IPQC 检验链路(状态 CAS/不良率自动计算/不合格自动 NCR/afterCommit MQ 回传 ERP)。
 * 幂等键约定: 单号:0:inspect(带每次操作 nonce)。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/qc/ipqc")
public class AppPdaIpqcController {

    private final IPdaQcService pdaQcService;

    /** 待检列表 (PENDING/INSPECTING, 扫单号/物料码/工单号定位) */
    @SaCheckPermission("mes:qc:ipqc:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaQcItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaQcService.ipqcTodo(keyword, pageQuery);
    }

    /** 检验单详情 */
    @SaCheckPermission("mes:qc:ipqc:list")
    @GetMapping("/{ipqcId}")
    public R<PdaQcDetailVo> detail(@PathVariable Long ipqcId) {
        return R.ok(pdaQcService.ipqcDetail(ipqcId));
    }

    /** 提交巡检结果 (幂等; 不合格自动 NCR 并 MQ 回传 ERP) */
    @SaCheckPermission("mes:qc:ipqc:inspect")
    @PdaIdempotent
    @Log(title = "PDA IPQC巡检", businessType = BusinessType.UPDATE)
    @PostMapping("/{ipqcId}/inspect")
    public R<MesActionResultVo> inspect(@PathVariable Long ipqcId, @Validated @RequestBody @NotNull PdaQcInspectBo bo) {
        return R.ok(pdaQcService.ipqcInspect(ipqcId, bo));
    }
}
