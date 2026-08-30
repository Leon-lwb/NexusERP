package com.nexus.erp.wm.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.annotation.PdaIdempotent;
import com.nexus.erp.wm.domain.bo.pda.PdaLineConfirmBo;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaReceiveDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.service.IPdaReceiveService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 收货作业接口 (登录态, 契约见 doc/plans/2026-08-30-M1-执行计划.md 阶段 2)
 * <p>
 * 状态机: 行级核销(累计实收+写上架明细) → 上架确认(APPROVED→SHELVED) → 收货过账(SHELVED→FINISHED)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/receive")
public class AppPdaReceiveController {

    private final IPdaReceiveService pdaReceiveService;

    /** 待收货单据列表 (扫码/单号定位) */
    @SaCheckPermission("wm:receive:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaReceiveService.todo(keyword, pageQuery);
    }

    /** 收货单详情 (头+行) */
    @SaCheckPermission("wm:receive:list")
    @GetMapping("/{receiveId}")
    public R<PdaReceiveDetailVo> detail(@PathVariable Long receiveId) {
        return R.ok(pdaReceiveService.detail(receiveId));
    }

    /** 行级扫码核销 (幂等键: 单号:行号:confirm) */
    @SaCheckPermission("wm:receive:edit")
    @PdaIdempotent
    @Log(title = "PDA收货核销", businessType = BusinessType.UPDATE)
    @PostMapping("/{receiveId}/line/confirm")
    public R<PdaActionResultVo> confirmLine(@PathVariable Long receiveId, @Validated @RequestBody @NotNull PdaLineConfirmBo bo) {
        return R.ok(pdaReceiveService.confirmLine(receiveId, bo));
    }

    /** 上架确认: 扫库位码填充明细后整单上架 (幂等键: 单号:0:shelf:库位码) */
    @SaCheckPermission("wm:receive:edit")
    @PdaIdempotent
    @Log(title = "PDA收货上架", businessType = BusinessType.UPDATE)
    @PutMapping("/{receiveId}/shelf")
    public R<PdaActionResultVo> shelf(@PathVariable Long receiveId, @Validated @RequestBody @NotNull PdaShelfBo bo) {
        return R.ok(pdaReceiveService.shelf(receiveId, bo));
    }

    /** 执行收货过账 (幂等键: 单号:0:finish) */
    @SaCheckPermission("wm:receive:edit")
    @PdaIdempotent
    @Log(title = "PDA收货过账", businessType = BusinessType.UPDATE)
    @PostMapping("/{receiveId}/finish")
    public R<PdaActionResultVo> finish(@PathVariable Long receiveId) {
        return R.ok(pdaReceiveService.finish(receiveId));
    }
}
