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
import com.nexus.core.bd.domain.BdUnitOfMeasure;
import com.nexus.core.bd.service.IBdUnitOfMeasureService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 计量单位Controller
 *
 * @author nexus
 * @date 2025-09-30
 */
@Api(tags = "计量单位Controller")
@RestController
@RequestMapping("/bd/measure")
public class BdUnitOfMeasureController extends BaseController
{
    @Autowired
    private IBdUnitOfMeasureService bdUnitOfMeasureService;

    /**
     * 查询计量单位列表
     */
    @ApiOperation("查询计量单位列表")
    @PreAuthorize("@ss.hasPermi('bd:measure:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdUnitOfMeasure bdUnitOfMeasure)
    {
        startPage();
        List<BdUnitOfMeasure> list = bdUnitOfMeasureService.selectBdUnitOfMeasureList(bdUnitOfMeasure);
        return getDataTable(list);
    }

    /**
     * 导出计量单位列表
     */
    @ApiOperation("导出计量单位列表")
    @PreAuthorize("@ss.hasPermi('bd:measure:export')")
    @Log(title = "计量单位", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdUnitOfMeasure bdUnitOfMeasure)
    {
        List<BdUnitOfMeasure> list = bdUnitOfMeasureService.selectBdUnitOfMeasureList(bdUnitOfMeasure);
        ExcelUtil<BdUnitOfMeasure> util = new ExcelUtil<BdUnitOfMeasure>(BdUnitOfMeasure.class);
        util.exportExcel(response, list, "计量单位数据");
    }

    /**
     * 获取计量单位详细信息
     */
    @ApiOperation("获取计量单位列表")
    @PreAuthorize("@ss.hasPermi('bd:measure:query')")
    @GetMapping(value = "/{uomId}")
    public AjaxResult getInfo(@PathVariable("uomId") Long uomId)
    {
        return success(bdUnitOfMeasureService.selectBdUnitOfMeasureByUomId(uomId));
    }

    /**
     * 新增计量单位
     */
    @ApiOperation("新增计量单位详细信息")
    @PreAuthorize("@ss.hasPermi('bd:measure:add')")
    @Log(title = "计量单位", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdUnitOfMeasure bdUnitOfMeasure)
    {
        return toAjax(bdUnitOfMeasureService.insertBdUnitOfMeasure(bdUnitOfMeasure));
    }

    /**
     * 修改计量单位
     */
    @ApiOperation("修改计量单位详细信息")
    @PreAuthorize("@ss.hasPermi('bd:measure:edit')")
    @Log(title = "计量单位", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdUnitOfMeasure bdUnitOfMeasure)
    {
        return toAjax(bdUnitOfMeasureService.updateBdUnitOfMeasure(bdUnitOfMeasure));
    }

    /**
     * 删除计量单位
     */
    @ApiOperation("删除计量单位信息")
    @PreAuthorize("@ss.hasPermi('bd:measure:remove')")
    @Log(title = "计量单位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{uomIds}")
    public AjaxResult remove(@PathVariable Long[] uomIds)
    {
        return toAjax(bdUnitOfMeasureService.deleteBdUnitOfMeasureByUomIds(uomIds));
    }

    /**
     * 查询所有基本单位不分页
     */
    @ApiOperation("查询所有基本单位不分页")
    @PreAuthorize("@ss.hasPermi('bd:measure:list')")
    @GetMapping("/all")
    public AjaxResult all()
    {
        return success(bdUnitOfMeasureService.selectBdUnitOfMeasureAll());
    }
}
