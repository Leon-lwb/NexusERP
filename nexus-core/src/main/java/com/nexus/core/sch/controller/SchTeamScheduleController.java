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
import com.nexus.core.sch.domain.SchTeamSchedule;
import com.nexus.core.sch.service.ISchTeamScheduleService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 班组排班（模板）Controller
 *
 * @author nexus
 * @date 2025-12-30
 */
@Api(tags = "班组排班（模板）Controller")
@RestController
@RequestMapping("/sch/teamSchedule")
public class SchTeamScheduleController extends BaseController {
    @Autowired
    private ISchTeamScheduleService schTeamScheduleService;

    /**
     * 查询班组排班（模板）列表
     */
    @ApiOperation("查询班组排班（模板）列表")
    @PreAuthorize("@ss.hasPermi('sch:teamSchedule:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchTeamSchedule schTeamSchedule) {
        startPage();
        List<SchTeamSchedule> list = schTeamScheduleService.selectSchTeamScheduleList(schTeamSchedule);
        return getDataTable(list);
    }

    /**
     * 导出班组排班（模板）列表
     */
    @ApiOperation("导出班组排班（模板）列表")
    @PreAuthorize("@ss.hasPermi('sch:teamSchedule:export')")
    @Log(title = "班组排班（模板）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchTeamSchedule schTeamSchedule) {
        List<SchTeamSchedule> list = schTeamScheduleService.selectSchTeamScheduleList(schTeamSchedule);
        ExcelUtil<SchTeamSchedule> util = new ExcelUtil<SchTeamSchedule>(SchTeamSchedule.class);
        util.exportExcel(response, list, "班组排班（模板）数据");
    }

    /**
     * 获取班组排班（模板）详细信息
     */
    @ApiOperation("获取班组排班（模板）列表")
    @PreAuthorize("@ss.hasPermi('sch:teamSchedule:query')")
    @GetMapping(value = "/{teamScheduleId}")
    public AjaxResult getInfo(@PathVariable("teamScheduleId") Long teamScheduleId) {
        return success(schTeamScheduleService.selectSchTeamScheduleByTeamScheduleId(teamScheduleId));
    }

    /**
     * 新增班组排班（模板）
     */
    @ApiOperation("新增班组排班（模板）详细信息")
    @PreAuthorize("@ss.hasPermi('sch:teamSchedule:add')")
    @Log(title = "班组排班（模板）", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchTeamSchedule schTeamSchedule) {
        return toAjax(schTeamScheduleService.insertSchTeamSchedule(schTeamSchedule));
    }

    /**
     * 修改班组排班（模板）
     */
    @ApiOperation("修改班组排班（模板）详细信息")
    @PreAuthorize("@ss.hasPermi('sch:teamSchedule:edit')")
    @Log(title = "班组排班（模板）", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchTeamSchedule schTeamSchedule) {
        return toAjax(schTeamScheduleService.updateSchTeamSchedule(schTeamSchedule));
    }

    /**
     * 删除班组排班（模板）
     */
    @ApiOperation("删除班组排班（模板）信息")
    @PreAuthorize("@ss.hasPermi('sch:teamSchedule:remove')")
    @Log(title = "班组排班（模板）", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamScheduleIds}")
    public AjaxResult remove(@PathVariable Long[] teamScheduleIds) {
        return toAjax(schTeamScheduleService.deleteSchTeamScheduleByTeamScheduleIds(teamScheduleIds));
    }
}
