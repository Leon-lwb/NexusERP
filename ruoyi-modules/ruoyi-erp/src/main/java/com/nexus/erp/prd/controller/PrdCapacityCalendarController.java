package com.nexus.erp.prd.controller;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产能日历Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/prd/capacityCalendar")
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
}
