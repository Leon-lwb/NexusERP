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
import com.nexus.core.wm.domain.WmWarehouse;
import com.nexus.core.wm.service.IWmWarehouseService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 仓库设置Controller
 *
 * @author nexus
 * @date 2025-09-30
 */
@Api(tags = "仓库设置Controller")
@RestController
@RequestMapping("/wm/warehouse")
public class WmWarehouseController extends BaseController
{
    @Autowired
    private IWmWarehouseService wmWarehouseService;

    /**
     * 查询仓库设置列表
     */
    @ApiOperation("查询仓库设置列表")
    @PreAuthorize("@ss.hasPermi('wm:warehouse:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmWarehouse wmWarehouse)
    {
        startPage();
        List<WmWarehouse> list = wmWarehouseService.selectWmWarehouseList(wmWarehouse);
        return getDataTable(list);
    }

    /**
     * 导出仓库设置列表
     */
    @ApiOperation("导出仓库设置列表")
    @PreAuthorize("@ss.hasPermi('wm:warehouse:export')")
    @Log(title = "仓库设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmWarehouse wmWarehouse)
    {
        List<WmWarehouse> list = wmWarehouseService.selectWmWarehouseList(wmWarehouse);
        ExcelUtil<WmWarehouse> util = new ExcelUtil<WmWarehouse>(WmWarehouse.class);
        util.exportExcel(response, list, "仓库设置数据");
    }

    /**
     * 获取仓库设置详细信息
     */
    @ApiOperation("获取仓库设置列表")
    @PreAuthorize("@ss.hasPermi('wm:warehouse:query')")
    @GetMapping(value = "/{warehouseId}")
    public AjaxResult getInfo(@PathVariable("warehouseId") Long warehouseId)
    {
        return success(wmWarehouseService.selectWmWarehouseByWarehouseId(warehouseId));
    }

    /**
     * 新增仓库设置
     */
    @ApiOperation("新增仓库设置详细信息")
    @PreAuthorize("@ss.hasPermi('wm:warehouse:add')")
    @Log(title = "仓库设置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmWarehouse wmWarehouse)
    {
        return toAjax(wmWarehouseService.insertWmWarehouse(wmWarehouse));
    }

    /**
     * 修改仓库设置
     */
    @ApiOperation("修改仓库设置详细信息")
    @PreAuthorize("@ss.hasPermi('wm:warehouse:edit')")
    @Log(title = "仓库设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmWarehouse wmWarehouse)
    {
        return toAjax(wmWarehouseService.updateWmWarehouse(wmWarehouse));
    }

    /**
     * 删除仓库设置
     */
    @ApiOperation("删除仓库设置信息")
    @PreAuthorize("@ss.hasPermi('wm:warehouse:remove')")
    @Log(title = "仓库设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{warehouseIds}")
    public AjaxResult remove(@PathVariable Long[] warehouseIds)
    {
        return toAjax(wmWarehouseService.deleteWmWarehouseByWarehouseIds(warehouseIds));
    }
}
