package org.dromara.erp.sch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.sch.domain.bo.SchAttendanceBo;
import org.dromara.erp.sch.domain.vo.SchAttendanceVo;
import org.dromara.erp.sch.service.ISchAttendanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考勤记录Controller
 *
 * @author nexus
 * @date 2026-01-06
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/attendance")
public class SchAttendanceController extends BaseController {

    private final ISchAttendanceService schAttendanceService;

    @SaCheckPermission("sch:attendance:list")
    @GetMapping("/list")
    public TableDataInfo<SchAttendanceVo> list(SchAttendanceBo bo, PageQuery pageQuery) {
        return schAttendanceService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:attendance:export")
    @Log(title = "考勤记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchAttendanceBo bo, HttpServletResponse response) {
        List<SchAttendanceVo> list = schAttendanceService.selectList(bo);
        ExcelUtil.exportExcel(list, "考勤记录数据", SchAttendanceVo.class, response);
    }

    @SaCheckPermission("sch:attendance:query")
    @GetMapping("/{attendanceId}")
    public R<SchAttendanceVo> getInfo(@PathVariable Long attendanceId) {
        return R.ok(schAttendanceService.selectById(attendanceId));
    }

    @SaCheckPermission("sch:attendance:add")
    @Log(title = "考勤记录", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchAttendanceBo bo) {
        return toAjax(schAttendanceService.insertByBo(bo));
    }

    @SaCheckPermission("sch:attendance:edit")
    @Log(title = "考勤记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchAttendanceBo bo) {
        return toAjax(schAttendanceService.updateByBo(bo));
    }

    @SaCheckPermission("sch:attendance:remove")
    @Log(title = "考勤记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{attendanceIds}")
    public R<Void> remove(@PathVariable Long[] attendanceIds) {
        return toAjax(schAttendanceService.deleteByIds(attendanceIds));
    }
}
