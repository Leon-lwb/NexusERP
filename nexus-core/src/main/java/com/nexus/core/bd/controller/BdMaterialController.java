package com.nexus.core.bd.controller;

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
import com.nexus.core.bd.domain.BdMaterial;
import com.nexus.core.bd.service.IBdMaterialService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 物料产品主Controller
 *
 * @author nexus
 * @date 2025-10-04
 */
@Api(tags = "物料产品主Controller")
@RestController
@RequestMapping("/bd/material")
public class BdMaterialController extends BaseController
{
    @Autowired
    private IBdMaterialService bdMaterialService;

    /**
     * 查询物料产品主列表
     */
    @ApiOperation("查询物料产品主列表")
    @PreAuthorize("@ss.hasPermi('bd:material:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdMaterial bdMaterial)
    {
        startPage();
        List<BdMaterial> list = bdMaterialService.selectBdMaterialList(bdMaterial);
        return getDataTable(list);
    }

    /**
     * 导出物料产品主列表
     */
    @ApiOperation("导出物料产品主列表")
    @PreAuthorize("@ss.hasPermi('bd:material:export')")
    @Log(title = "物料产品主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdMaterial bdMaterial)
    {
        List<BdMaterial> list = bdMaterialService.selectBdMaterialList(bdMaterial);
        ExcelUtil<BdMaterial> util = new ExcelUtil<BdMaterial>(BdMaterial.class);
        util.exportExcel(response, list, "物料产品主数据");
    }

    /**
     * 获取物料产品主详细信息
     */
    @ApiOperation("获取物料产品主列表")
    @PreAuthorize("@ss.hasPermi('bd:material:query')")
    @GetMapping(value = "/{materialId}")
    public AjaxResult getInfo(@PathVariable("materialId") Long materialId)
    {
        return success(bdMaterialService.selectBdMaterialByMaterialId(materialId));
    }

    /**
     * 新增物料产品主
     */
    @ApiOperation("新增物料产品主详细信息")
    @PreAuthorize("@ss.hasPermi('bd:material:add')")
    @Log(title = "物料产品主", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdMaterial bdMaterial)
    {
        return toAjax(bdMaterialService.insertBdMaterial(bdMaterial));
    }

    /**
     * 修改物料产品主
     */
    @ApiOperation("修改物料产品主详细信息")
    @PreAuthorize("@ss.hasPermi('bd:material:edit')")
    @Log(title = "物料产品主", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdMaterial bdMaterial)
    {
        return toAjax(bdMaterialService.updateBdMaterial(bdMaterial));
    }

    /**
     * 删除物料产品主
     */
    @ApiOperation("删除物料产品主信息")
    @PreAuthorize("@ss.hasPermi('bd:material:remove')")
    @Log(title = "物料产品主", businessType = BusinessType.DELETE)
    @DeleteMapping("/{materialIds}")
    public AjaxResult remove(@PathVariable Long[] materialIds)
    {
        return toAjax(bdMaterialService.deleteBdMaterialByMaterialIds(materialIds));
    }
}
