package com.nexus.core.bd.controller;

import com.nexus.common.utils.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
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
import com.nexus.core.bd.domain.BdMaterialCategory;
import com.nexus.core.bd.service.IBdMaterialCategoryService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 物料产品分类Controller
 *
 * @author nexus
 * @date 2025-10-01
 */
@Api(tags = "物料产品分类Controller")
@RestController
@RequestMapping("/bd/category")
public class BdMaterialCategoryController extends BaseController
{
    @Autowired
    private IBdMaterialCategoryService bdMaterialCategoryService;

    /**
     * 查询物料产品分类列表
     */
    @ApiOperation("查询物料产品分类列表")
    @PreAuthorize("@ss.hasPermi('bd:category:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdMaterialCategory bdMaterialCategory)
    {
        startPage();
        List<BdMaterialCategory> list = bdMaterialCategoryService.selectBdMaterialCategoryList(bdMaterialCategory);
        return getDataTable(list);
    }

    /**
     * 导出物料产品分类列表
     */
    @ApiOperation("导出物料产品分类列表")
    @PreAuthorize("@ss.hasPermi('bd:category:export')")
    @Log(title = "物料产品分类", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdMaterialCategory bdMaterialCategory)
    {
        List<BdMaterialCategory> list = bdMaterialCategoryService.selectBdMaterialCategoryList(bdMaterialCategory);
        ExcelUtil<BdMaterialCategory> util = new ExcelUtil<BdMaterialCategory>(BdMaterialCategory.class);
        util.exportExcel(response, list, "物料产品分类数据");
    }

    /**
     * 获取物料产品分类详细信息
     */
    @ApiOperation("获取物料产品分类列表")
    @PreAuthorize("@ss.hasPermi('bd:category:query')")
    @GetMapping(value = "/{categoryId}")
    public AjaxResult getInfo(@PathVariable("categoryId") Long categoryId)
    {
        return success(bdMaterialCategoryService.selectBdMaterialCategoryByCategoryId(categoryId));
    }

    /**
     * 新增物料产品分类
     */
    @ApiOperation("新增物料产品分类详细信息")
    @PreAuthorize("@ss.hasPermi('bd:category:add')")
    @Log(title = "物料产品分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdMaterialCategory bdMaterialCategory)
    {
        return toAjax(bdMaterialCategoryService.insertBdMaterialCategory(bdMaterialCategory));
    }

    /**
     * 修改物料产品分类
     */
    @ApiOperation("修改物料产品分类详细信息")
    @PreAuthorize("@ss.hasPermi('bd:category:edit')")
    @Log(title = "物料产品分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdMaterialCategory bdMaterialCategory)
    {
        return toAjax(bdMaterialCategoryService.updateBdMaterialCategory(bdMaterialCategory));
    }

    /**
     * 删除物料产品分类
     */
    @ApiOperation("删除物料产品分类信息")
    @PreAuthorize("@ss.hasPermi('bd:category:remove')")
    @Log(title = "物料产品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public AjaxResult remove(@PathVariable Long[] categoryIds)
    {
        return toAjax(bdMaterialCategoryService.deleteBdMaterialCategoryByCategoryIds(categoryIds));
    }

    /**
     * 获取物料产品分类列表（排除指定ID的子部门）
     */
    @PreAuthorize("@ss.hasPermi('bd:category:list')")
    @GetMapping("/list/exclude/{categoryId}")
    public AjaxResult excludeChild(@PathVariable(value = "categoryId", required = false) Long[] categoryId)
    {
        List<BdMaterialCategory> list = bdMaterialCategoryService.selectBdMaterialCategoryList(new BdMaterialCategory());
        // 移除指定部门及其子部门
        list.removeIf(c ->
                ArrayUtils.contains(categoryId, c.getCategoryId()) ||
                        ArrayUtils.contains(StringUtils.split(c.getPathIds(), ","), String.valueOf(categoryId))
        );

        return success(list);
    }
}
