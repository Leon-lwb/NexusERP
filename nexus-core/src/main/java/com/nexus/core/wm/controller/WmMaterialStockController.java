package com.nexus.core.wm.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.core.wm.domain.WmMaterialStock;
import com.nexus.core.wm.service.IWmMaterialStockService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 库存清单Controller
 *
 * @author nexus
 * @date 2025-12-03
 */
@Api(tags = "库存清单Controller")
@RestController
@RequestMapping("/wm/stock")
public class WmMaterialStockController extends BaseController
{
    @Autowired
    private IWmMaterialStockService wmMaterialStockService;

    /**
     * 查询库存清单列表
     */
    @ApiOperation("查询库存清单列表")
    @PreAuthorize("@ss.hasPermi('wm:stock:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmMaterialStock wmMaterialStock)
    {
        startPage();
        List<WmMaterialStock> list = wmMaterialStockService.selectWmMaterialStockList(wmMaterialStock);
        return getDataTable(list);
    }

    /**
     * 导出库存清单列表
     */
    @ApiOperation("导出库存清单列表")
    @PreAuthorize("@ss.hasPermi('wm:stock:export')")
    @Log(title = "库存清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmMaterialStock wmMaterialStock)
    {
        List<WmMaterialStock> list = wmMaterialStockService.selectWmMaterialStockList(wmMaterialStock);
        ExcelUtil<WmMaterialStock> util = new ExcelUtil<WmMaterialStock>(WmMaterialStock.class);
        util.exportExcel(response, list, "库存清单数据");
    }

    /**
     * 获取库存清单详细信息
     */
    @ApiOperation("获取库存清单列表")
    @PreAuthorize("@ss.hasPermi('wm:stock:query')")
    @GetMapping(value = "/{stockId}")
    public AjaxResult getInfo(@PathVariable("stockId") Long stockId)
    {
        return success(wmMaterialStockService.selectWmMaterialStockByStockId(stockId));
    }

    /**
     * 新增库存清单
     */
    @ApiOperation("新增库存清单详细信息")
    @PreAuthorize("@ss.hasPermi('wm:stock:add')")
    @Log(title = "库存清单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmMaterialStock wmMaterialStock)
    {
        return toAjax(wmMaterialStockService.insertWmMaterialStock(wmMaterialStock));
    }

    /**
     * 修改库存清单
     */
    @ApiOperation("修改库存清单详细信息")
    @PreAuthorize("@ss.hasPermi('wm:stock:edit')")
    @Log(title = "库存清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmMaterialStock wmMaterialStock)
    {
        return toAjax(wmMaterialStockService.updateWmMaterialStock(wmMaterialStock));
    }

    /**
     * 删除库存清单
     */
    @ApiOperation("删除库存清单信息")
    @PreAuthorize("@ss.hasPermi('wm:stock:remove')")
    @Log(title = "库存清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{stockIds}")
    public AjaxResult remove(@PathVariable Long[] stockIds)
    {
        return toAjax(wmMaterialStockService.deleteWmMaterialStockByStockIds(stockIds));
    }
}
