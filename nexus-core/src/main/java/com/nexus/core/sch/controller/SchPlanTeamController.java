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
import com.nexus.core.sch.domain.SchPlanTeam;
import com.nexus.core.sch.service.ISchPlanTeamService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 计划班组关联Controller
 *
 * @author nexus
 * @date 2025-12-26
 */
@Api(tags = "计划班组关联Controller")
@RestController
@RequestMapping("/sch/planteam")
public class SchPlanTeamController extends BaseController
{
    @Autowired
    private ISchPlanTeamService schPlanTeamService;

    /**
     * 查询计划班组关联列表
     */
    @ApiOperation("查询计划班组关联列表")
    @PreAuthorize("@ss.hasPermi('sch:planteam:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchPlanTeam schPlanTeam)
    {
        startPage();
        List<SchPlanTeam> list = schPlanTeamService.selectSchPlanTeamList(schPlanTeam);
        return getDataTable(list);
    }

    /**
     * 导出计划班组关联列表
     */
    @ApiOperation("导出计划班组关联列表")
    @PreAuthorize("@ss.hasPermi('sch:planteam:export')")
    @Log(title = "计划班组关联", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchPlanTeam schPlanTeam)
    {
        List<SchPlanTeam> list = schPlanTeamService.selectSchPlanTeamList(schPlanTeam);
        ExcelUtil<SchPlanTeam> util = new ExcelUtil<SchPlanTeam>(SchPlanTeam.class);
        util.exportExcel(response, list, "计划班组关联数据");
    }

    /**
     * 获取计划班组关联详细信息
     */
    @ApiOperation("获取计划班组关联列表")
    @PreAuthorize("@ss.hasPermi('sch:planteam:query')")
    @GetMapping(value = "/{recordId}")
    public AjaxResult getInfo(@PathVariable("recordId") Long recordId)
    {
        return success(schPlanTeamService.selectSchPlanTeamByRecordId(recordId));
    }

    /**
     * 新增计划班组关联
     */
    @ApiOperation("新增计划班组关联详细信息")
    @PreAuthorize("@ss.hasPermi('sch:planteam:add')")
    @Log(title = "计划班组关联", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchPlanTeam schPlanTeam)
    {
        return toAjax(schPlanTeamService.insertSchPlanTeam(schPlanTeam));
    }

    /**
     * 修改计划班组关联
     */
    @ApiOperation("修改计划班组关联详细信息")
    @PreAuthorize("@ss.hasPermi('sch:planteam:edit')")
    @Log(title = "计划班组关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchPlanTeam schPlanTeam)
    {
        return toAjax(schPlanTeamService.updateSchPlanTeam(schPlanTeam));
    }

    /**
     * 删除计划班组关联
     */
    @ApiOperation("删除计划班组关联信息")
    @PreAuthorize("@ss.hasPermi('sch:planteam:remove')")
    @Log(title = "计划班组关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(schPlanTeamService.deleteSchPlanTeamByRecordIds(recordIds));
    }
}
