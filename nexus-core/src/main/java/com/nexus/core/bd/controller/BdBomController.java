package com.nexus.core.bd.controller;

import com.nexus.core.bd.domain.dto.BomDetailDTO;
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
import com.nexus.core.bd.domain.BdBom;
import com.nexus.core.bd.service.IBdBomService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 产品BOMController
 *
 * @author nexus
 * @date 2025-10-18
 */
@Api(tags = "产品BOMController")
@RestController
@RequestMapping("/bd/bom")
public class BdBomController extends BaseController
{
    @Autowired
    private IBdBomService bdBomService;

    /**
     * 查询产品BOM列表
     */
    @ApiOperation("查询产品BOM列表")
    @PreAuthorize("@ss.hasPermi('bd:bom:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdBom bdBom)
    {
        startPage();
        List<BdBom> list = bdBomService.selectBdBomList(bdBom);
        return getDataTable(list);
    }

    /**
     * 导出产品BOM列表
     */
    @ApiOperation("导出产品BOM列表")
    @PreAuthorize("@ss.hasPermi('bd:bom:export')")
    @Log(title = "产品BOM", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdBom bdBom)
    {
        List<BdBom> list = bdBomService.selectBdBomList(bdBom);
        ExcelUtil<BdBom> util = new ExcelUtil<BdBom>(BdBom.class);
        util.exportExcel(response, list, "产品BOM数据");
    }

    /**
     * 获取产品BOM详细信息
     */
    @ApiOperation("获取产品BOM列表")
    @PreAuthorize("@ss.hasPermi('bd:bom:query')")
    @GetMapping(value = "/{bomId}")
    public AjaxResult getInfo(@PathVariable("bomId") Long bomId)
    {
        return success(bdBomService.selectBdBomByBomId(bomId));
    }

    /**
     * 新增产品BOM
     */
    @ApiOperation("新增产品BOM详细信息")
    @PreAuthorize("@ss.hasPermi('bd:bom:add')")
    @Log(title = "产品BOM", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdBom bdBom)
    {
        return toAjax(bdBomService.insertBdBom(bdBom));
    }

    /**
     * 修改产品BOM
     */
    @ApiOperation("修改产品BOM详细信息")
    @PreAuthorize("@ss.hasPermi('bd:bom:edit')")
    @Log(title = "产品BOM", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdBom bdBom)
    {
        return toAjax(bdBomService.updateBdBom(bdBom));
    }

    /**
     * 删除产品BOM
     */
    @ApiOperation("删除产品BOM信息")
    @PreAuthorize("@ss.hasPermi('bd:bom:remove')")
    @Log(title = "产品BOM", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bomIds}")
    public AjaxResult remove(@PathVariable Long[] bomIds)
    {
        return toAjax(bdBomService.deleteBdBomByBomIds(bomIds));
    }

    /**
     * 创建BOM明细
     */
    @ApiOperation("创建BOM明细")
    @PreAuthorize("@ss.hasPermi('bd:bom:add')")
    @Log(title = "产品BOM明细", businessType = BusinessType.INSERT)
    @PostMapping("/detail")
    public AjaxResult addDetail(@RequestBody BomDetailDTO bomDetailDTO)
    {
        return toAjax(bdBomService.insertBomDetail(bomDetailDTO));
    }
}
