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
import com.nexus.core.sch.domain.SchEmployeeSchedule;
import com.nexus.core.sch.service.ISchEmployeeScheduleService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 员工排班明细Controller
 *
 * @author nexus
 * @date 2025-12-30
 */
@Api(tags = "员工排班明细Controller")
@RestController
@RequestMapping("/sch/employeeSchedule")
public class SchEmployeeScheduleController extends BaseController {
    @Autowired
    private ISchEmployeeScheduleService schEmployeeScheduleService;

    /**
     * 查询员工排班明细列表
     */
    @ApiOperation("查询员工排班明细列表")
    @PreAuthorize("@ss.hasPermi('sch:employeeSchedule:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchEmployeeSchedule schEmployeeSchedule) {
        startPage();
        List<SchEmployeeSchedule> list = schEmployeeScheduleService.selectSchEmployeeScheduleList(schEmployeeSchedule);
        return getDataTable(list);
    }

    /**
     * 导出员工排班明细列表
     */
    @ApiOperation("导出员工排班明细列表")
    @PreAuthorize("@ss.hasPermi('sch:employeeSchedule:export')")
    @Log(title = "员工排班明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchEmployeeSchedule schEmployeeSchedule) {
        List<SchEmployeeSchedule> list = schEmployeeScheduleService.selectSchEmployeeScheduleList(schEmployeeSchedule);
        ExcelUtil<SchEmployeeSchedule> util = new ExcelUtil<SchEmployeeSchedule>(SchEmployeeSchedule.class);
        util.exportExcel(response, list, "员工排班明细数据");
    }

    /**
     * 获取员工排班明细详细信息
     */
    @ApiOperation("获取员工排班明细列表")
    @PreAuthorize("@ss.hasPermi('sch:employeeSchedule:query')")
    @GetMapping(value = "/{scheduleId}")
    public AjaxResult getInfo(@PathVariable("scheduleId") Long scheduleId) {
        return success(schEmployeeScheduleService.selectSchEmployeeScheduleByScheduleId(scheduleId));
    }

    /**
     * 新增员工排班明细
     */
    @ApiOperation("新增员工排班明细详细信息")
    @PreAuthorize("@ss.hasPermi('sch:employeeSchedule:add')")
    @Log(title = "员工排班明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchEmployeeSchedule schEmployeeSchedule) {
        return toAjax(schEmployeeScheduleService.insertSchEmployeeSchedule(schEmployeeSchedule));
    }

    /**
     * 修改员工排班明细
     */
    @ApiOperation("修改员工排班明细详细信息")
    @PreAuthorize("@ss.hasPermi('sch:employeeSchedule:edit')")
    @Log(title = "员工排班明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchEmployeeSchedule schEmployeeSchedule) {
        return toAjax(schEmployeeScheduleService.updateSchEmployeeSchedule(schEmployeeSchedule));
    }

    /**
     * 删除员工排班明细
     */
    @ApiOperation("删除员工排班明细信息")
    @PreAuthorize("@ss.hasPermi('sch:employeeSchedule:remove')")
    @Log(title = "员工排班明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{scheduleIds}")
    public AjaxResult remove(@PathVariable Long[] scheduleIds) {
        return toAjax(schEmployeeScheduleService.deleteSchEmployeeScheduleByScheduleIds(scheduleIds));
    }
}
