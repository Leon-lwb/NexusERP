package com.nexus.erp.prd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.prd.domain.bo.PrdCapacityCalendarBo;
import com.nexus.erp.prd.domain.vo.PrdCapacityCalendarVo;
import com.nexus.erp.prd.service.IPrdCapacityCalendarService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 产能日历Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/capacityCalendar")
public class PrdCapacityCalendarController extends BaseController {

    private final IPrdCapacityCalendarService capacityCalendarService;

    /**
     * 查询产能日历列表
     */
    @GetMapping("/list")
    public TableDataInfo<PrdCapacityCalendarVo> list(PrdCapacityCalendarBo bo, PageQuery pageQuery) {
        return capacityCalendarService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出产能日历列表
     */
    @Log(title = "产能日历", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdCapacityCalendarBo bo, HttpServletResponse response) {
        List<PrdCapacityCalendarVo> list = capacityCalendarService.selectList(bo);
        ExcelUtil.exportExcel(list, "产能日历", PrdCapacityCalendarVo.class, response);
    }

    /**
     * 获取产能日历详细信息
     */
    @GetMapping("/{calendarId}")
    public R<PrdCapacityCalendarVo> getInfo(@NotNull(message = "日历ID不能为空") @PathVariable Long calendarId) {
        return R.ok(capacityCalendarService.selectById(calendarId));
    }

    /**
     * 新增产能日历
     */
    @Log(title = "产能日历", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody PrdCapacityCalendarBo bo) {
        return toAjax(capacityCalendarService.insertByBo(bo));
    }

    /**
     * 修改产能日历
     */
    @Log(title = "产能日历", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody PrdCapacityCalendarBo bo) {
        return toAjax(capacityCalendarService.updateByBo(bo));
    }

    /**
     * 删除产能日历
     */
    @Log(title = "产能日历", businessType = BusinessType.DELETE)
    @DeleteMapping("/{calendarIds}")
    public R<Void> remove(@NotEmpty(message = "日历ID不能为空") @PathVariable Long[] calendarIds) {
        return toAjax(capacityCalendarService.deleteWithValidByIds(List.of(calendarIds), true));
    }

    @SaCheckPermission("prd:capacityCalendar:edit")
    @Log(title = "产能日历-生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public R<Void> generate(@RequestParam Long planId,
                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
                            @RequestParam String objectType,
                            @RequestParam Long objectId,
                            @RequestParam String objectCode,
                            @RequestParam String objectName) {
        capacityCalendarService.generateFromSchedule(planId, startDate, endDate, objectType, objectId, objectCode, objectName);
        return R.ok();
    }

    @SaCheckPermission("prd:capacityCalendar:query")
    @GetMapping("/available")
    public R<List<PrdCapacityCalendarVo>> available(@RequestParam String objectType,
                                                    @RequestParam Long objectId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return R.ok(capacityCalendarService.queryAvailableCapacity(objectType, objectId, startDate, endDate));
    }

    @SaCheckPermission("prd:capacityCalendar:edit")
    @Log(title = "产能日历-占用", businessType = BusinessType.UPDATE)
    @PutMapping("/occupy")
    public R<Void> occupy(@RequestParam Long calendarId, @RequestParam BigDecimal hours) {
        capacityCalendarService.occupyCapacity(calendarId, hours);
        return R.ok();
    }

    @SaCheckPermission("prd:capacityCalendar:edit")
    @Log(title = "产能日历-释放", businessType = BusinessType.UPDATE)
    @PutMapping("/release")
    public R<Void> release(@RequestParam Long calendarId, @RequestParam BigDecimal hours) {
        capacityCalendarService.releaseCapacity(calendarId, hours);
        return R.ok();
    }
}
