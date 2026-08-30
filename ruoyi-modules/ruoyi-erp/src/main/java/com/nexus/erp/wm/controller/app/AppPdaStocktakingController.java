package com.nexus.erp.wm.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.annotation.PdaIdempotent;
import com.nexus.erp.wm.domain.bo.pda.PdaCountBo;
import com.nexus.erp.wm.domain.bo.pda.PdaStocktakingCreateBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaStocktakingDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.service.IPdaStocktakingService;
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
 * PDA 盘点接口 (明盲盘 + 临时盘点现场建单 + 动碰盘)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/stocktaking")
public class AppPdaStocktakingController {

    private final IPdaStocktakingService pdaStocktakingService;

    @SaCheckPermission("wm:stocktaking:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaStocktakingService.todo(keyword, pageQuery);
    }

    /** 临时盘点现场建单并生成明细 (幂等键: 仓库:时间戳) */
    @SaCheckPermission("wm:stocktaking:add")
    @PdaIdempotent
    @Log(title = "PDA临时盘点建单", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public R<PdaStocktakingDetailVo> create(@Validated @RequestBody @NotNull PdaStocktakingCreateBo bo) {
        return R.ok(pdaStocktakingService.create(bo));
    }

    @SaCheckPermission("wm:stocktaking:list")
    @GetMapping("/{planId}")
    public R<PdaStocktakingDetailVo> detail(@PathVariable Long planId) {
        return R.ok(pdaStocktakingService.detail(planId));
    }

    /** 生成盘点明细行 (沿用既有库存快照逻辑) */
    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "PDA盘点生成明细", businessType = BusinessType.UPDATE)
    @PutMapping("/{planId}/generateLines")
    public R<PdaActionResultVo> generate(@PathVariable Long planId) {
        return R.ok(pdaStocktakingService.generate(planId));
    }

    /** 行级实盘录入, 覆盖语义可重复改数 (幂等键: 单号:行号:count) */
    @SaCheckPermission("wm:stocktaking:edit")
    @PdaIdempotent
    @Log(title = "PDA盘点实盘录入", businessType = BusinessType.UPDATE)
    @PostMapping("/{planId}/line/count")
    public R<PdaActionResultVo> count(@PathVariable Long planId, @Validated @RequestBody @NotNull PdaCountBo bo) {
        return R.ok(pdaStocktakingService.count(planId, bo));
    }

    /** 差异过账 (幂等键: 单号:0:post) */
    @SaCheckPermission("wm:stocktaking:edit")
    @PdaIdempotent
    @Log(title = "PDA盘点过账", businessType = BusinessType.UPDATE)
    @PostMapping("/{planId}/post")
    public R<PdaActionResultVo> post(@PathVariable Long planId) {
        return R.ok(pdaStocktakingService.post(planId));
    }
}
