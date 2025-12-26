package com.nexus.core.sch.controller;

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
import com.nexus.core.sch.domain.SchPlan;
import com.nexus.core.sch.service.ISchPlanService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 排班计划Controller
 *
 * @author nexus
 * @date 2025-12-25
 */
@Api(tags = "排班计划Controller")
@RestController
@RequestMapping("/sch/plan")
public class SchPlanController extends BaseController
{
    @Autowired
    private ISchPlanService schPlanService;

    /**
     * 查询排班计划列表
     */
    @ApiOperation("查询排班计划列表")
    @PreAuthorize("@ss.hasPermi('sch:plan:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchPlan schPlan)
    {
        startPage();
        List<SchPlan> list = schPlanService.selectSchPlanList(schPlan);
        return getDataTable(list);
    }

    /**
     * 导出排班计划列表
     */
    @ApiOperation("导出排班计划列表")
    @PreAuthorize("@ss.hasPermi('sch:plan:export')")
    @Log(title = "排班计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchPlan schPlan)
    {
        List<SchPlan> list = schPlanService.selectSchPlanList(schPlan);
        ExcelUtil<SchPlan> util = new ExcelUtil<SchPlan>(SchPlan.class);
        util.exportExcel(response, list, "排班计划数据");
    }

    /**
     * 获取排班计划详细信息
     */
    @ApiOperation("获取排班计划列表")
    @PreAuthorize("@ss.hasPermi('sch:plan:query')")
    @GetMapping(value = "/{planId}")
    public AjaxResult getInfo(@PathVariable("planId") Long planId)
    {
        return success(schPlanService.selectSchPlanByPlanId(planId));
    }

    /**
     * 新增排班计划
     */
    @ApiOperation("新增排班计划详细信息")
    @PreAuthorize("@ss.hasPermi('sch:plan:add')")
    @Log(title = "排班计划", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchPlan schPlan)
    {
        return toAjax(schPlanService.insertSchPlan(schPlan));
    }

    /**
     * 修改排班计划
     */
    @ApiOperation("修改排班计划详细信息")
    @PreAuthorize("@ss.hasPermi('sch:plan:edit')")
    @Log(title = "排班计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchPlan schPlan)
    {
        return toAjax(schPlanService.updateSchPlan(schPlan));
    }

    /**
     * 删除排班计划
     */
    @ApiOperation("删除排班计划信息")
    @PreAuthorize("@ss.hasPermi('sch:plan:remove')")
    @Log(title = "排班计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public AjaxResult remove(@PathVariable Long[] planIds)
    {
        return toAjax(schPlanService.deleteSchPlanByPlanIds(planIds));
    }
}
