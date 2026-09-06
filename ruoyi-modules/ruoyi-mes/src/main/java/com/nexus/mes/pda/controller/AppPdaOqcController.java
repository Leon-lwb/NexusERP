package com.nexus.mes.pda.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.pda.PdaIdempotent;
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
 * PDA OQC 出货检验接口 (M3 批次3; 契约见 nexus-pda doc/plans/2026-08-30-M3-执行计划.md 批次 3)
 * <p>
 * 提交委托既有 OQC 检验链路(状态 CAS/不合格自动 NCR/afterCommit MQ 回传)。
 * 幂等键约定: 单号:0:inspect(带每次操作 nonce)。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/mes/qc/oqc")
public class AppPdaOqcController {

    private final IPdaQcService pdaQcService;

    /** 待检列表 (PENDING/INSPECTING, 扫单号/物料码定位) */
    @SaCheckPermission("mes:qc:oqc:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaQcItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaQcService.oqcTodo(keyword, pageQuery);
    }

    /** 检验单详情 */
    @SaCheckPermission("mes:qc:oqc:list")
    @GetMapping("/{oqcId}")
    public R<PdaQcDetailVo> detail(@PathVariable Long oqcId) {
        return R.ok(pdaQcService.oqcDetail(oqcId));
    }

    /** 提交检验结果 (幂等; 不合格自动 NCR 并 MQ 回传) */
    @SaCheckPermission("mes:qc:oqc:inspect")
    @PdaIdempotent
    @Log(title = "PDA OQC检验", businessType = BusinessType.UPDATE)
    @PostMapping("/{oqcId}/inspect")
    public R<MesActionResultVo> inspect(@PathVariable Long oqcId, @Validated @RequestBody @NotNull PdaQcInspectBo bo) {
        return R.ok(pdaQcService.oqcInspect(oqcId, bo));
    }
}
