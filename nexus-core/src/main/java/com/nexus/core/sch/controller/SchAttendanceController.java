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
import com.nexus.core.sch.domain.SchAttendance;
import com.nexus.core.sch.service.ISchAttendanceService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 考勤记录Controller
 *
 * @author nexus
 * @date 2026-01-06
 */
@Api(tags = "考勤记录Controller")
@RestController
@RequestMapping("/sch/attendance")
public class SchAttendanceController extends BaseController
{
    @Autowired
    private ISchAttendanceService schAttendanceService;

    /**
     * 查询考勤记录列表
     */
    @ApiOperation("查询考勤记录列表")
    @PreAuthorize("@ss.hasPermi('sch:attendance:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchAttendance schAttendance)
    {
        startPage();
        List<SchAttendance> list = schAttendanceService.selectSchAttendanceList(schAttendance);
        return getDataTable(list);
    }

    /**
     * 导出考勤记录列表
     */
    @ApiOperation("导出考勤记录列表")
    @PreAuthorize("@ss.hasPermi('sch:attendance:export')")
    @Log(title = "考勤记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchAttendance schAttendance)
    {
        List<SchAttendance> list = schAttendanceService.selectSchAttendanceList(schAttendance);
        ExcelUtil<SchAttendance> util = new ExcelUtil<SchAttendance>(SchAttendance.class);
        util.exportExcel(response, list, "考勤记录数据");
    }

    /**
     * 获取考勤记录详细信息
     */
    @ApiOperation("获取考勤记录列表")
    @PreAuthorize("@ss.hasPermi('sch:attendance:query')")
    @GetMapping(value = "/{attendanceId}")
    public AjaxResult getInfo(@PathVariable("attendanceId") Long attendanceId)
    {
        return success(schAttendanceService.selectSchAttendanceByAttendanceId(attendanceId));
    }

    /**
     * 新增考勤记录
     */
    @ApiOperation("新增考勤记录详细信息")
    @PreAuthorize("@ss.hasPermi('sch:attendance:add')")
    @Log(title = "考勤记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchAttendance schAttendance)
    {
        return toAjax(schAttendanceService.insertSchAttendance(schAttendance));
    }

    /**
     * 修改考勤记录
     */
    @ApiOperation("修改考勤记录详细信息")
    @PreAuthorize("@ss.hasPermi('sch:attendance:edit')")
    @Log(title = "考勤记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchAttendance schAttendance)
    {
        return toAjax(schAttendanceService.updateSchAttendance(schAttendance));
    }

    /**
     * 删除考勤记录
     */
    @ApiOperation("删除考勤记录信息")
    @PreAuthorize("@ss.hasPermi('sch:attendance:remove')")
    @Log(title = "考勤记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{attendanceIds}")
    public AjaxResult remove(@PathVariable Long[] attendanceIds)
    {
        return toAjax(schAttendanceService.deleteSchAttendanceByAttendanceIds(attendanceIds));
    }
}
