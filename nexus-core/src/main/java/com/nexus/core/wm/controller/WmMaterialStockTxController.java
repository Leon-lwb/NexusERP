package com.nexus.core.wm.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.core.wm.domain.WmMaterialStockTx;
import com.nexus.core.wm.service.IWmMaterialStockTxService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 库存事务Controller
 * 
 * @author nexus
 * @date 2025-12-12
 */
@Validated
@RestController
@RequestMapping("/wm/stocktx")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WmMaterialStockTxController extends BaseController {

    private final IWmMaterialStockTxService wmMaterialStockTxService;

    /**
     * 查询库存事务列表
     */
    @PreAuthorize("@ss.hasPermi('wm:stocktx:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmMaterialStockTx wmMaterialStockTx) {
        startPage();
        List<WmMaterialStockTx> list = wmMaterialStockTxService.selectWmMaterialStockTxList(wmMaterialStockTx);
        return getDataTable(list);
    }

    /**
     * 导出库存事务列表
     */
    @PreAuthorize("@ss.hasPermi('wm:stocktx:export')")
    @Log(title = "库存事务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmMaterialStockTx wmMaterialStockTx) {
        List<WmMaterialStockTx> list = wmMaterialStockTxService.selectWmMaterialStockTxList(wmMaterialStockTx);
        ExcelUtil<WmMaterialStockTx> util = new ExcelUtil<WmMaterialStockTx>(WmMaterialStockTx.class);
        util.exportExcel(response, list, "库存事务数据");
    }

    /**
     * 获取库存事务详细信息
     */
    @PreAuthorize("@ss.hasPermi('wm:stocktx:query')")
    @GetMapping(value = "/{txId}")
    public AjaxResult getInfo(@PathVariable("txId") Long txId) {
        return AjaxResult.success(wmMaterialStockTxService.selectWmMaterialStockTxByTxId(txId));
    }

    /**
     * 新增库存事务
     */
    @PreAuthorize("@ss.hasPermi('wm:stocktx:add')")
    @Log(title = "库存事务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmMaterialStockTx wmMaterialStockTx) {
        return toAjax(wmMaterialStockTxService.insertWmMaterialStockTx(wmMaterialStockTx));
    }

    /**
     * 修改库存事务
     */
    @PreAuthorize("@ss.hasPermi('wm:stocktx:edit')")
    @Log(title = "库存事务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmMaterialStockTx wmMaterialStockTx) {
        return toAjax(wmMaterialStockTxService.updateWmMaterialStockTx(wmMaterialStockTx));
    }

    /**
     * 删除库存事务
     */
    @PreAuthorize("@ss.hasPermi('wm:stocktx:remove')")
    @Log(title = "库存事务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{txIds}")
    public AjaxResult remove(@PathVariable Long[] txIds) {
        return toAjax(wmMaterialStockTxService.deleteWmMaterialStockTxByTxIds(txIds));
    }
}