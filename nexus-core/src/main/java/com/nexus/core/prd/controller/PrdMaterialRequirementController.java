package com.nexus.core.prd.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.stream.Collectors;
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
import com.nexus.core.prd.domain.PrdMaterialRequirement;
import com.nexus.core.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.core.prd.service.IPrdMaterialRequirementService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 物料清单Controller
 *
 * @author nexus
 * @date 2025-11-21
 */
@Api(tags = "物料清单Controller")
@RestController
@RequestMapping("/prd/requirement")
public class PrdMaterialRequirementController extends BaseController
{
    @Autowired
    private IPrdMaterialRequirementService prdMaterialRequirementService;

    /**
     * 查询物料清单列表
     */
    @ApiOperation("查询物料清单列表")
    @PreAuthorize("@ss.hasPermi('prd:requirement:list')")
    @GetMapping("/list")
    public TableDataInfo list(PrdMaterialRequirement prdMaterialRequirement)
    {
        startPage();
        List<PrdMaterialRequirement> list = prdMaterialRequirementService.selectPrdMaterialRequirementList(prdMaterialRequirement);
        return getDataTable(list);
    }

    /**
     * 导出物料清单列表
     */
    @ApiOperation("导出物料清单列表")
    @PreAuthorize("@ss.hasPermi('prd:requirement:export')")
    @Log(title = "物料清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrdMaterialRequirement prdMaterialRequirement)
    {
        List<PrdMaterialRequirement> list = prdMaterialRequirementService.selectPrdMaterialRequirementList(prdMaterialRequirement);
        ExcelUtil<PrdMaterialRequirement> util = new ExcelUtil<PrdMaterialRequirement>(PrdMaterialRequirement.class);
        util.exportExcel(response, list, "物料清单数据");
    }

    /**
     * 获取物料清单详细信息
     */
    @ApiOperation("获取物料清单列表")
    @PreAuthorize("@ss.hasPermi('prd:requirement:query')")
    @GetMapping(value = "/{requirementId}")
    public AjaxResult getInfo(@PathVariable("requirementId") Long requirementId)
    {
        return success(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(requirementId));
    }

    /**
     * 新增物料清单
     */
    @ApiOperation("新增物料清单详细信息")
    @PreAuthorize("@ss.hasPermi('prd:requirement:add')")
    @Log(title = "物料清单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrdMaterialRequirement prdMaterialRequirement)
    {
        return toAjax(prdMaterialRequirementService.insertPrdMaterialRequirement(prdMaterialRequirement));
    }

    /**
     * 修改物料清单
     */
    @ApiOperation("修改物料清单详细信息")
    @PreAuthorize("@ss.hasPermi('prd:requirement:edit')")
    @Log(title = "物料清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrdMaterialRequirement prdMaterialRequirement)
    {
        return toAjax(prdMaterialRequirementService.updatePrdMaterialRequirement(prdMaterialRequirement));
    }

    /**
     * 删除物料清单
     */
    @ApiOperation("删除物料清单信息")
    @PreAuthorize("@ss.hasPermi('prd:requirement:remove')")
    @Log(title = "物料清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{requirementIds}")
    public AjaxResult remove(@PathVariable Long[] requirementIds)
    {
        return toAjax(prdMaterialRequirementService.deletePrdMaterialRequirementByRequirementIds(requirementIds));
    }

    /**
     * 根据生产计划编号查询物料清单子表（无分页）
     */
    @ApiOperation("根据生产计划编号查询物料清单子表")
    @PreAuthorize("@ss.hasPermi('prd:requirement:list')")
    @GetMapping("/listDetailsByPlanNo/{planNo}")
    public AjaxResult listDetailsByPlanNo(@PathVariable String planNo) {
        // 1. 先根据planNo查询主表数据（包含明细）
        PrdMaterialRequirement queryParam = new PrdMaterialRequirement();
        queryParam.setPlanNo(planNo);
        List<PrdMaterialRequirement> mainList = prdMaterialRequirementService.selectPrdMaterialRequirementListWithDetails(queryParam);
        
        // 2. 收集所有子表数据
        List<PrdMaterialRequirementDetail> detailList = mainList.stream()
                .filter(main -> main.getPrdMaterialRequirementDetailList() != null)
                .flatMap(main -> main.getPrdMaterialRequirementDetailList().stream())
                .collect(Collectors.toList());
                
        return AjaxResult.success(detailList);
    }
}