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
 * 产能日历 Controller
 * <p>
 * 提供产能日历的 RESTful API 接口，支持产能日历的查询、导出、生成、占用与释放等操作。
 * 产能日历用于管理车间、工位或生产线在特定日期和班次下的可用产能。
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
     * 分页查询产能日历列表
     * <p>
     * 权限标识：{@code prd:capacityCalendar:list}</p>
     *
     * @param bo        查询参数，包含日期、对象类型、对象ID等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 产能日历分页列表
     */
    @SaCheckPermission("prd:capacityCalendar:list")
    @GetMapping("/list")
    public TableDataInfo<PrdCapacityCalendarVo> list(PrdCapacityCalendarBo bo, PageQuery pageQuery) {
        return capacityCalendarService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出产能日历列表到 Excel
     * <p>权限标识：{@code prd:capacityCalendar:export}</p>
     * <p>日志记录：title="产能日历"，businessType=EXPORT</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:capacityCalendar:export")
    @Log(title = "产能日历", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdCapacityCalendarBo bo, HttpServletResponse response) {
        List<PrdCapacityCalendarVo> list = capacityCalendarService.selectList(bo);
        ExcelUtil.exportExcel(list, "产能日历", PrdCapacityCalendarVo.class, response);
    }

    /**
     * 根据主键获取产能日历详细信息
     * <p>
     * 权限标识：{@code prd:capacityCalendar:query}</p>
     *
     * @param calendarId 产能日历 ID
     * @return 产能日历详细信息
     */
    @SaCheckPermission("prd:capacityCalendar:query")
    @GetMapping("/{calendarId}")
    public R<PrdCapacityCalendarVo> getInfo(@NotNull(message = "日历ID不能为空") @PathVariable Long calendarId) {
        return R.ok(capacityCalendarService.selectById(calendarId));
    }

    /**
     * 新增产能日历
     * <p>
     * 权限标识：{@code prd:capacityCalendar:add}</p>
     * <p>日志记录：title="产能日历"，businessType=INSERT</p>
     *
     * @param bo 产能日历业务对象，包含日期、班次、可用工时等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:capacityCalendar:add")
    @Log(title = "产能日历", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody PrdCapacityCalendarBo bo) {
        return toAjax(capacityCalendarService.insertByBo(bo));
    }

    /**
     * 修改产能日历
     * <p>
     * 权限标识：{@code prd:capacityCalendar:edit}</p>
     * <p>日志记录：title="产能日历"，businessType=UPDATE</p>
     *
     * @param bo 产能日历业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:capacityCalendar:edit")
    @Log(title = "产能日历", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody PrdCapacityCalendarBo bo) {
        return toAjax(capacityCalendarService.updateByBo(bo));
    }

    /**
     * 批量删除产能日历
     * <p>
     * 权限标识：{@code prd:capacityCalendar:remove}</p>
     * <p>日志记录：title="产能日历"，businessType=DELETE</p>
     *
     * @param calendarIds 要删除的产能日历 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:capacityCalendar:remove")
    @Log(title = "产能日历", businessType = BusinessType.DELETE)
    @DeleteMapping("/{calendarIds}")
    public R<Void> remove(@NotEmpty(message = "日历ID不能为空") @PathVariable Long[] calendarIds) {
        return toAjax(capacityCalendarService.deleteWithValidByIds(List.of(calendarIds), true));
    }

    /**
     * 根据排班计划生成产能日历
     * <p>
     * 根据指定的排班计划，在日期范围内为对象生成产能日历记录。
     * 权限标识：{@code prd:capacityCalendar:edit}</p>
     * <p>日志记录：title="产能日历-生成"，businessType=INSERT</p>
     *
     * @param planId     排班计划 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @param objectType 对象类型（WORKSHOP-车间, WORKSTATION-工位, LINE-生产线）
     * @param objectId   对象 ID
     * @param objectCode 对象编码
     * @param objectName 对象名称
     * @return 操作结果
     */
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

    /**
     * 查询指定对象的可用产能列表
     * <p>
     * 权限标识：{@code prd:capacityCalendar:query}</p>
     *
     * @param objectType 对象类型
     * @param objectId   对象 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @return 可用产能日历列表
     */
    @SaCheckPermission("prd:capacityCalendar:query")
    @GetMapping("/available")
    public R<List<PrdCapacityCalendarVo>> available(@RequestParam String objectType,
                                                    @RequestParam Long objectId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return R.ok(capacityCalendarService.queryAvailableCapacity(objectType, objectId, startDate, endDate));
    }

    /**
     * 占用产能
     * <p>
     * 将指定产能日历的剩余产能中占用指定工时数。
     * 权限标识：{@code prd:capacityCalendar:edit}</p>
     * <p>日志记录：title="产能日历-占用"，businessType=UPDATE</p>
     *
     * @param calendarId 产能日历 ID
     * @param hours      占用工时数
     * @return 操作结果
     */
    @SaCheckPermission("prd:capacityCalendar:edit")
    @Log(title = "产能日历-占用", businessType = BusinessType.UPDATE)
    @PutMapping("/occupy")
    public R<Void> occupy(@RequestParam Long calendarId, @RequestParam BigDecimal hours) {
        capacityCalendarService.occupyCapacity(calendarId, hours);
        return R.ok();
    }

    /**
     * 释放产能
     * <p>
     * 将指定产能日历中已占用的工时释放回剩余产能。
     * 权限标识：{@code prd:capacityCalendar:edit}</p>
     * <p>日志记录：title="产能日历-释放"，businessType=UPDATE</p>
     *
     * @param calendarId 产能日历 ID
     * @param hours      释放工时数
     * @return 操作结果
     */
    @SaCheckPermission("prd:capacityCalendar:edit")
    @Log(title = "产能日历-释放", businessType = BusinessType.UPDATE)
    @PutMapping("/release")
    public R<Void> release(@RequestParam Long calendarId, @RequestParam BigDecimal hours) {
        capacityCalendarService.releaseCapacity(calendarId, hours);
        return R.ok();
    }
}
