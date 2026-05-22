package org.dromara.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.wm.domain.bo.WmStockTakingPlanBo;
import org.dromara.erp.wm.domain.vo.WmStockTakingPlanVo;
import org.dromara.erp.wm.service.IWmStockTakingPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stocktaking")
public class WmStockTakingPlanController extends BaseController {

    private final IWmStockTakingPlanService wmStockTakingPlanService;

    @SaCheckPermission("wm:stocktaking:list")
    @GetMapping("/list")
    public TableDataInfo<WmStockTakingPlanVo> list(WmStockTakingPlanBo bo, PageQuery pageQuery) {
        return wmStockTakingPlanService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:stocktaking:export")
    @Log(title = "盘点管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStockTakingPlanBo bo, HttpServletResponse response) {
        List<WmStockTakingPlanVo> list = wmStockTakingPlanService.selectList(bo);
        ExcelUtil.exportExcel(list, "盘点数据", WmStockTakingPlanVo.class, response);
    }

    @SaCheckPermission("wm:stocktaking:query")
    @GetMapping("/{planId}")
    public R<WmStockTakingPlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(wmStockTakingPlanService.selectById(planId));
    }

    @SaCheckPermission("wm:stocktaking:add")
    @Log(title = "盘点管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmStockTakingPlanBo bo) {
        return toAjax(wmStockTakingPlanService.insertByBo(bo));
    }

    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "盘点管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStockTakingPlanBo bo) {
        return toAjax(wmStockTakingPlanService.updateByBo(bo));
    }

    @SaCheckPermission("wm:stocktaking:remove")
    @Log(title = "盘点管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@PathVariable Long[] planIds) {
        return toAjax(wmStockTakingPlanService.deleteByIds(planIds));
    }

    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "盘点管理", businessType = BusinessType.UPDATE)
    @PutMapping("/generateLines/{planId}")
    public R<Void> generateLines(@PathVariable Long planId) {
        wmStockTakingPlanService.generateLines(planId);
        return R.ok();
    }

    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "盘点管理", businessType = BusinessType.UPDATE)
    @PutMapping("/postAdjustment/{planId}")
    public R<Void> postAdjustment(@PathVariable Long planId) {
        wmStockTakingPlanService.postAdjustment(planId);
        return R.ok();
    }
}
