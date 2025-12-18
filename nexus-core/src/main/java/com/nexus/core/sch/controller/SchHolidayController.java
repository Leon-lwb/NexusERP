package com.nexus.core.sch.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.nexus.core.sch.domain.SchHoliday;
import com.nexus.core.sch.service.ISchHolidayService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 节假日设置Controller
 *
 * @author nexus
 * @date 2025-12-17
 */
@Api(tags = "节假日设置Controller")
@RestController
@RequestMapping("/sch/holiday")
public class SchHolidayController extends BaseController
{
    @Autowired
    private ISchHolidayService schHolidayService;

    /**
     * 查询节假日设置列表
     */
    @ApiOperation("查询节假日设置列表")
    @PreAuthorize("@ss.hasPermi('sch:holiday:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchHoliday schHoliday)
    {
        startPage();
        List<SchHoliday> list = schHolidayService.selectSchHolidayList(schHoliday);
        return getDataTable(list);
    }

    /**
     * 导出节假日设置列表
     */
    @ApiOperation("导出节假日设置列表")
    @PreAuthorize("@ss.hasPermi('sch:holiday:export')")
    @Log(title = "节假日设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchHoliday schHoliday)
    {
        List<SchHoliday> list = schHolidayService.selectSchHolidayList(schHoliday);
        ExcelUtil<SchHoliday> util = new ExcelUtil<SchHoliday>(SchHoliday.class);
        util.exportExcel(response, list, "节假日设置数据");
    }

    /**
     * 获取节假日设置详细信息
     */
    @ApiOperation("获取节假日设置列表")
    @PreAuthorize("@ss.hasPermi('sch:holiday:query')")
    @GetMapping(value = "/{holidayId}")
    public AjaxResult getInfo(@PathVariable("holidayId") Long holidayId)
    {
        return success(schHolidayService.selectSchHolidayByHolidayId(holidayId));
    }

    /**
     * 新增节假日设置
     */
    @ApiOperation("新增节假日设置详细信息")
    @PreAuthorize("@ss.hasPermi('sch:holiday:add')")
    @Log(title = "节假日设置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchHoliday schHoliday) {
        // 参数校验
        if (schHoliday == null) {
            return AjaxResult.error("节假日信息不能为空");
        }

        try {
            // 构造查询条件，检查该日期是否已存在节假日记录
            LambdaQueryWrapper<SchHoliday> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SchHoliday::getHolidayDate, schHoliday.getHolidayDate());

            // 查询该日期是否已存在记录
            SchHoliday existingHoliday = schHolidayService.getOne(queryWrapper);

            // 如果该日期已存在记录，则更新原有记录；否则新增记录
            if (existingHoliday != null) {
                // 设置ID以确保更新操作能正确定位到记录
                schHoliday.setHolidayId(existingHoliday.getHolidayId());
                return toAjax(schHolidayService.updateSchHoliday(schHoliday));
            } else {
                return toAjax(schHolidayService.insertSchHoliday(schHoliday));
            }
        } catch (Exception e) {
            return AjaxResult.error("节假日保存失败：" + e.getMessage());
        }
    }


    /**
     * 修改节假日设置
     */
    @ApiOperation("修改节假日设置详细信息")
    @PreAuthorize("@ss.hasPermi('sch:holiday:edit')")
    @Log(title = "节假日设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchHoliday schHoliday)
    {
        return toAjax(schHolidayService.updateSchHoliday(schHoliday));
    }

    /**
     * 删除节假日设置
     */
    @ApiOperation("删除节假日设置信息")
    @PreAuthorize("@ss.hasPermi('sch:holiday:remove')")
    @Log(title = "节假日设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{holidayIds}")
    public AjaxResult remove(@PathVariable Long[] holidayIds)
    {
        return toAjax(schHolidayService.deleteSchHolidayByHolidayIds(holidayIds));
    }
}
