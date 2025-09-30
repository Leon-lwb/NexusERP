package com.nexus.core.bd.controller;

import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.core.bd.domain.BdVendor;
import com.nexus.core.bd.service.IBdVendorService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商Controller
 *
 * @author nexus
 * @date 2025-09-22
 */
@Api(tags = "供应商Controller")
@RestController
@RequestMapping("/core/vendor")
public class BdVendorController extends BaseController
{
    @Autowired
    private IBdVendorService bdVendorService;

    /**
     * 查询供应商列表
     */
    @ApiOperation("查询供应商列表")
    @PreAuthorize("@ss.hasPermi('core:vendor:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdVendor bdVendor)
    {
        startPage();
        List<BdVendor> list = bdVendorService.selectBdVendorList(bdVendor);
        return getDataTable(list);
    }

    /**
     * 导出供应商列表
     */
    @ApiOperation("导出供应商列表")
    @PreAuthorize("@ss.hasPermi('core:vendor:export')")
    @Log(title = "供应商", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdVendor bdVendor)
    {
        List<BdVendor> list = bdVendorService.selectBdVendorList(bdVendor);
        ExcelUtil<BdVendor> util = new ExcelUtil<BdVendor>(BdVendor.class);
        util.exportExcel(response, list, "供应商数据");
    }

    /**
     * 获取供应商详细信息
     */
    @ApiOperation("获取供应商列表")
    @PreAuthorize("@ss.hasPermi('core:vendor:query')")
    @GetMapping(value = "/{vendorId}")
    public AjaxResult getInfo(@PathVariable("vendorId") Long vendorId)
    {
        return success(bdVendorService.selectBdVendorByVendorId(vendorId));
    }

    /**
     * 新增供应商
     */
    @ApiOperation("新增供应商详细信息")
    @PreAuthorize("@ss.hasPermi('core:vendor:add')")
    @Log(title = "供应商", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdVendor bdVendor)
    {
        return toAjax(bdVendorService.insertBdVendor(bdVendor));
    }

    /**
     * 修改供应商
     */
    @ApiOperation("修改供应商详细信息")
    @PreAuthorize("@ss.hasPermi('core:vendor:edit')")
    @Log(title = "供应商", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdVendor bdVendor)
    {
        return toAjax(bdVendorService.updateBdVendor(bdVendor));
    }

    /**
     * 删除供应商
     */
    @ApiOperation("删除供应商信息")
    @PreAuthorize("@ss.hasPermi('core:vendor:remove')")
    @Log(title = "供应商", businessType = BusinessType.DELETE)
    @DeleteMapping("/{vendorIds}")
    public AjaxResult remove(@PathVariable Long[] vendorIds)
    {
        return toAjax(bdVendorService.deleteBdVendorByVendorIds(vendorIds));
    }
}
