package com.nexus.erp.wm.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.pda.PdaIdempotent;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.bo.pda.PdaTransferTakeoffBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTransferDetailVo;
import com.nexus.erp.wm.service.IPdaTransferService;
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
 * PDA 移库接口 (两段式: 行级下架核销 → 扫目标库位上架过账; execute 为兜底直通)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/transfer")
public class AppPdaTransferController {

    private final IPdaTransferService pdaTransferService;

    @SaCheckPermission("wm:transfer:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaTransferService.todo(keyword, pageQuery);
    }

    @SaCheckPermission("wm:transfer:list")
    @GetMapping("/{transferId}")
    public R<PdaTransferDetailVo> detail(@PathVariable Long transferId) {
        return R.ok(pdaTransferService.detail(transferId));
    }

    /** 行级下架核销 (幂等键: 单号:行号:takeoff) */
    @SaCheckPermission("wm:transfer:edit")
    @PdaIdempotent
    @Log(title = "PDA移库下架", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/line/takeoff")
    public R<PdaActionResultVo> takeoff(
        @PathVariable Long transferId,
        @Validated @RequestBody @NotNull PdaTransferTakeoffBo bo) {
        return R.ok(pdaTransferService.takeoff(transferId, bo));
    }

    /** 上架确认过账 (幂等键: 单号:0:shelf) */
    @SaCheckPermission("wm:transfer:edit")
    @PdaIdempotent
    @Log(title = "PDA移库上架", businessType = BusinessType.UPDATE)
    @PutMapping("/{transferId}/shelf")
    public R<PdaActionResultVo> shelf(
        @PathVariable Long transferId,
        @Validated @RequestBody @NotNull PdaShelfBo bo) {
        return R.ok(pdaTransferService.shelf(transferId, bo));
    }

    /** 整单执行移库兜底直通 (幂等键: 单号:0:execute) */
    @SaCheckPermission("wm:transfer:edit")
    @PdaIdempotent
    @Log(title = "PDA移库执行", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/execute")
    public R<PdaActionResultVo> execute(@PathVariable Long transferId) {
        return R.ok(pdaTransferService.execute(transferId));
    }
}
