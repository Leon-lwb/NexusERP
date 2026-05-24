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

    /**
     * 分页查询考勤记录列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 考勤记录分页列表
     */
    @SaCheckPermission("sch:attendance:list")
    @GetMapping("/list")
    public TableDataInfo<SchAttendanceVo> list(SchAttendanceBo bo, PageQuery pageQuery) {
        return schAttendanceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出考勤记录数据到 Excel
     * <p>权限标识：{@code sch:attendance:export}</p>
     * <p>日志记录：title="考勤记录"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("sch:attendance:export")
    @Log(title = "考勤记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchAttendanceBo bo, HttpServletResponse response) {
        List<SchAttendanceVo> list = schAttendanceService.selectList(bo);
        ExcelUtil.exportExcel(list, "考勤记录数据", SchAttendanceVo.class, response);
    }

    /**
     * 获取考勤记录详细信息
     * <p>权限标识：{@code sch:attendance:query}</p>
     *
     * @param attendanceId 考勤记录ID
     * @return 考勤记录详细信息
     */
    @SaCheckPermission("sch:attendance:query")
    @GetMapping("/{attendanceId}")
    public R<SchAttendanceVo> getInfo(@PathVariable Long attendanceId) {
        return R.ok(schAttendanceService.selectById(attendanceId));
    }

    /**
     * 新增考勤记录
     * <p>权限标识：{@code sch:attendance:add}</p>
     * <p>日志记录：title="考勤记录"，businessType=INSERT</p>
     *
     * @param bo 考勤记录业务对象
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:add")
    @Log(title = "考勤记录", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchAttendanceBo bo) {
        return toAjax(schAttendanceService.insertByBo(bo));
    }

    /**
     * 修改考勤记录
     * <p>权限标识：{@code sch:attendance:edit}</p>
     * <p>日志记录：title="考勤记录"，businessType=UPDATE</p>
     *
     * @param bo 考勤记录业务对象
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:edit")
    @Log(title = "考勤记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchAttendanceBo bo) {
        return toAjax(schAttendanceService.updateByBo(bo));
    }

    /**
     * 删除考勤记录
     * <p>权限标识：{@code sch:attendance:remove}</p>
     * <p>日志记录：title="考勤记录"，businessType=DELETE</p>
     *
     * @param attendanceIds 考勤记录ID数组
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:remove")
    @Log(title = "考勤记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{attendanceIds}")
    public R<Void> remove(@PathVariable Long[] attendanceIds) {
        return toAjax(schAttendanceService.deleteByIds(attendanceIds));
    }
}
