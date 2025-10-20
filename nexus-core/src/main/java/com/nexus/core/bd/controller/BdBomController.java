package com.nexus.core.bd.controller;

import com.nexus.core.bd.domain.dto.BomDetailDTO;
import com.nexus.core.bd.domain.vo.BomDetailVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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

    // ==================== BOM主表接口 ====================

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

    // ==================== BOM明细接口 ====================

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

    /**
     * 修改BOM明细
     */
    @ApiOperation("修改BOM明细")
    @PreAuthorize("@ss.hasPermi('bd:bom:edit')")
    @Log(title = "产品BOM明细", businessType = BusinessType.UPDATE)
    @PutMapping("/detail/{detailId}")
    public AjaxResult updateDetail(
            @ApiParam("明细ID") @PathVariable Long detailId,
            @ApiParam("BOM明细信息") @RequestBody BomDetailDTO bomDetailDTO)
    {
        bomDetailDTO.setDetailId(detailId);
        return toAjax(bdBomService.updateBomDetail(bomDetailDTO));
    }

    /**
     * 删除BOM明细
     */
    @ApiOperation("删除BOM明细")
    @PreAuthorize("@ss.hasPermi('bd:bom:remove')")
    @Log(title = "产品BOM明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/detail/{detailIds}")
    public AjaxResult removeDetails(
            @ApiParam("明细ID数组") @PathVariable Long[] detailIds)
    {
        return toAjax(bdBomService.deleteBomDetails(detailIds));
    }

    /**
     * 查询BOM明细列表
     */
    @ApiOperation("查询BOM明细列表")
    @PreAuthorize("@ss.hasPermi('bd:bom:list')")
    @GetMapping("/detail/list")
    public TableDataInfo listDetails(
            @ApiParam("BOM主表ID") @RequestParam Long bomId)
    {
        startPage();
        List<BomDetailVO> list = bdBomService.selectBomDetailList(bomId);
        return getDataTable(list);
    }

    /**
     * 获取BOM明细详细信息
     */
    @ApiOperation("获取BOM明细详细信息")
    @PreAuthorize("@ss.hasPermi('bd:bom:query')")
    @GetMapping("/detail/{detailId}")
    public AjaxResult getDetailInfo(
            @ApiParam("明细ID") @PathVariable Long detailId)
    {
        return success(bdBomService.selectBomDetailByDetailId(detailId));
    }

    /**
     * 批量保存BOM明细
     */
    @ApiOperation("批量保存BOM明细")
    @PreAuthorize("@ss.hasPermi('bd:bom:edit')")
    @Log(title = "产品BOM明细", businessType = BusinessType.UPDATE)
    @PostMapping("/detail/batch")
    public AjaxResult saveDetailsBatch(
            @ApiParam("BOM明细列表") @RequestBody List<BomDetailDTO> detailList)
    {
        return toAjax(bdBomService.saveBomDetailsBatch(detailList));
    }

    /**
     * 导出BOM明细
     */
//    @ApiOperation("导出BOM明细")
//    @PreAuthorize("@ss.hasPermi('bd:bom:export')")
//    @Log(title = "产品BOM明细", businessType = BusinessType.EXPORT)
//    @PostMapping("/detail/export")
//    public void exportDetails(
//            HttpServletResponse response,
//            @ApiParam("BOM主表ID") @RequestParam Long bomId)
//    {
//        List<BomDetailVO> list = bdBomService.selectBomDetailList(bomId);
//        ExcelUtil<BomDetailVO> util = new ExcelUtil<BomDetailVO>(BomDetailVO.class);
//        util.exportExcel(response, list, "BOM明细数据");
//    }

    /**
     * 获取完整BOM结构（主表+明细）
     */
    @ApiOperation("获取完整BOM结构")
    @PreAuthorize("@ss.hasPermi('bd:bom:query')")
    @GetMapping("/structure/{parentId}")
    public AjaxResult getBomStructure(
            @ApiParam("父项物料ID") @PathVariable Long parentId)
    {
        return success(bdBomService.getBomStructure(parentId));
    }

    /**
     * 复制BOM结构
     */
    @ApiOperation("复制BOM结构")
    @PreAuthorize("@ss.hasPermi('bd:bom:add')")
    @Log(title = "产品BOM", businessType = BusinessType.INSERT)
    @PostMapping("/copy")
    public AjaxResult copyBom(
            @ApiParam("源BOM ID") @RequestParam Long sourceBomId,
            @ApiParam("目标父项物料ID") @RequestParam Long targetParentId)
    {
        return toAjax(bdBomService.copyBom(sourceBomId, targetParentId));
    }

    /**
     * 获取当前有效BOM
     */
    @ApiOperation("获取当前有效BOM")
    @PreAuthorize("@ss.hasPermi('bd:bom:query')")
    @GetMapping("/current/{parentId}")
    public AjaxResult getCurrentBom(
            @ApiParam("父项物料ID") @PathVariable Long parentId)
    {
        return success(bdBomService.getCurrentBom(parentId));
    }
}
