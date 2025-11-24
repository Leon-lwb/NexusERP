package com.nexus.core.prd.controller;

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
import com.nexus.core.prd.domain.PrdProductionPlan;
import com.nexus.core.prd.service.IPrdProductionPlanService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 生产计划Controller
 *
 * @author nexus
 * @date 2025-11-13
 */
@Api(tags = "生产计划Controller")
@RestController
@RequestMapping("/prd/plan")
public class PrdProductionPlanController extends BaseController
{
    @Autowired
    private IPrdProductionPlanService prdProductionPlanService;

    /**
     * 查询生产计划列表
     */
    @ApiOperation("查询生产计划列表")
    @PreAuthorize("@ss.hasPermi('prd:plan:list')")
    @GetMapping("/list")
    public TableDataInfo list(PrdProductionPlan prdProductionPlan)
    {
        startPage();
        List<PrdProductionPlan> list = prdProductionPlanService.selectPrdProductionPlanList(prdProductionPlan);
        return getDataTable(list);
    }

    /**
     * 导出生产计划列表
     */
    @ApiOperation("导出生产计划列表")
    @PreAuthorize("@ss.hasPermi('prd:plan:export')")
    @Log(title = "生产计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrdProductionPlan prdProductionPlan)
    {
        List<PrdProductionPlan> list = prdProductionPlanService.selectPrdProductionPlanList(prdProductionPlan);
        ExcelUtil<PrdProductionPlan> util = new ExcelUtil<PrdProductionPlan>(PrdProductionPlan.class);
        util.exportExcel(response, list, "生产计划数据");
    }

    /**
     * 获取生产计划详细信息
     */
    @ApiOperation("获取生产计划列表")
    @PreAuthorize("@ss.hasPermi('prd:plan:query')")
    @GetMapping(value = "/{planId}")
    public AjaxResult getInfo(@PathVariable("planId") Long planId)
    {
        return success(prdProductionPlanService.selectPrdProductionPlanByPlanId(planId));
    }

    /**
     * 新增生产计划
     */
    @ApiOperation("新增生产计划详细信息")
    @PreAuthorize("@ss.hasPermi('prd:plan:add')")
    @Log(title = "生产计划", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrdProductionPlan prdProductionPlan)
    {
        return toAjax(prdProductionPlanService.insertPrdProductionPlan(prdProductionPlan));
    }

    /**
     * 修改生产计划
     */
    @ApiOperation("修改生产计划详细信息")
    @PreAuthorize("@ss.hasPermi('prd:plan:edit')")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrdProductionPlan prdProductionPlan)
    {
        return toAjax(prdProductionPlanService.updatePrdProductionPlan(prdProductionPlan));
    }

    /**
     * 删除生产计划
     */
    @ApiOperation("删除生产计划信息")
    @PreAuthorize("@ss.hasPermi('prd:plan:remove')")
    @Log(title = "生产计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public AjaxResult remove(@PathVariable Long[] planIds)
    {
        return toAjax(prdProductionPlanService.deletePrdProductionPlanByPlanIds(planIds));
    }

    /**
     * 生产计划版本更新
     */
    @ApiOperation("生产计划版本更新")
    @PreAuthorize("@ss.hasPermi('prd:plan:edit')")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping("/updateVersion")
    public AjaxResult updateVersion(@RequestBody PrdProductionPlan prdProductionPlan)
    {
        return toAjax(prdProductionPlanService.updatePrdProductionPlanVersion(prdProductionPlan));
    }

    /**
     * TODO
     * BOM计算
     */
    @ApiOperation("BOM计算")
    @PreAuthorize("@ss.hasPermi('prd:plan:edit')")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping("/createBom")
    public AjaxResult bom(@RequestBody PrdProductionPlan prdProductionPlan)
    {
        return success();
        //return toAjax(prdProductionPlanService.updatePrdProductionPlanBom(prdProductionPlan));
    }
}
