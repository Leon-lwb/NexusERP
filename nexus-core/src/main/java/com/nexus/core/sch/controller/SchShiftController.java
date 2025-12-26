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
import com.nexus.core.sch.domain.SchShift;
import com.nexus.core.sch.service.ISchShiftService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 班次定义Controller
 *
 * @author nexus
 * @date 2025-12-25
 */
@Api(tags = "班次定义Controller")
@RestController
@RequestMapping("/sch/shift")
public class SchShiftController extends BaseController
{
    @Autowired
    private ISchShiftService schShiftService;

    /**
     * 查询班次定义列表
     */
    @ApiOperation("查询班次定义列表")
    @PreAuthorize("@ss.hasPermi('sch:shift:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchShift schShift)
    {
        startPage();
        List<SchShift> list = schShiftService.selectSchShiftList(schShift);
        return getDataTable(list);
    }

    /**
     * 导出班次定义列表
     */
    @ApiOperation("导出班次定义列表")
    @PreAuthorize("@ss.hasPermi('sch:shift:export')")
    @Log(title = "班次定义", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchShift schShift)
    {
        List<SchShift> list = schShiftService.selectSchShiftList(schShift);
        ExcelUtil<SchShift> util = new ExcelUtil<SchShift>(SchShift.class);
        util.exportExcel(response, list, "班次定义数据");
    }

    /**
     * 获取班次定义详细信息
     */
    @ApiOperation("获取班次定义列表")
    @PreAuthorize("@ss.hasPermi('sch:shift:query')")
    @GetMapping(value = "/{shiftId}")
    public AjaxResult getInfo(@PathVariable("shiftId") Long shiftId)
    {
        return success(schShiftService.selectSchShiftByShiftId(shiftId));
    }

    /**
     * 新增班次定义
     */
    @ApiOperation("新增班次定义详细信息")
    @PreAuthorize("@ss.hasPermi('sch:shift:add')")
    @Log(title = "班次定义", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchShift schShift)
    {
        return toAjax(schShiftService.insertSchShift(schShift));
    }

    /**
     * 修改班次定义
     */
    @ApiOperation("修改班次定义详细信息")
    @PreAuthorize("@ss.hasPermi('sch:shift:edit')")
    @Log(title = "班次定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchShift schShift)
    {
        return toAjax(schShiftService.updateSchShift(schShift));
    }

    /**
     * 删除班次定义
     */
    @ApiOperation("删除班次定义信息")
    @PreAuthorize("@ss.hasPermi('sch:shift:remove')")
    @Log(title = "班次定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shiftIds}")
    public AjaxResult remove(@PathVariable Long[] shiftIds)
    {
        return toAjax(schShiftService.deleteSchShiftByShiftIds(shiftIds));
    }
}
