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
import org.dromara.erp.sch.domain.bo.SchEmployeeScheduleBo;
import org.dromara.erp.sch.domain.vo.SchEmployeeScheduleVo;
import org.dromara.erp.sch.service.ISchEmployeeScheduleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工排班明细Controller
 *
 * @author nexus
 * @date 2025-12-30
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/employeeSchedule")
public class SchEmployeeScheduleController extends BaseController {

    private final ISchEmployeeScheduleService schEmployeeScheduleService;

    /**
     * 分页查询员工排班明细列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 员工排班明细分页列表
     */
    @SaCheckPermission("sch:employeeSchedule:list")
    @GetMapping("/list")
    public TableDataInfo<SchEmployeeScheduleVo> list(SchEmployeeScheduleBo bo, PageQuery pageQuery) {
        return schEmployeeScheduleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出员工排班明细数据到 Excel
     * <p>权限标识：{@code sch:employeeSchedule:export}</p>
     * <p>日志记录：title="员工排班明细"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("sch:employeeSchedule:export")
    @Log(title = "员工排班明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchEmployeeScheduleBo bo, HttpServletResponse response) {
        List<SchEmployeeScheduleVo> list = schEmployeeScheduleService.selectList(bo);
        ExcelUtil.exportExcel(list, "员工排班明细数据", SchEmployeeScheduleVo.class, response);
    }

    /**
     * 获取员工排班明细详细信息
     * <p>权限标识：{@code sch:employeeSchedule:query}</p>
     *
     * @param scheduleId 排班明细ID
     * @return 员工排班明细详细信息
     */
    @SaCheckPermission("sch:employeeSchedule:query")
    @GetMapping("/{scheduleId}")
    public R<SchEmployeeScheduleVo> getInfo(@PathVariable Long scheduleId) {
        return R.ok(schEmployeeScheduleService.selectById(scheduleId));
    }

    /**
     * 新增员工排班明细
     * <p>权限标识：{@code sch:employeeSchedule:add}</p>
     * <p>日志记录：title="员工排班明细"，businessType=INSERT</p>
     *
     * @param bo 员工排班明细业务对象
     * @return 操作结果
     */
    @SaCheckPermission("sch:employeeSchedule:add")
    @Log(title = "员工排班明细", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchEmployeeScheduleBo bo) {
        return toAjax(schEmployeeScheduleService.insertByBo(bo));
    }

    /**
     * 修改员工排班明细
     * <p>权限标识：{@code sch:employeeSchedule:edit}</p>
     * <p>日志记录：title="员工排班明细"，businessType=UPDATE</p>
     *
     * @param bo 员工排班明细业务对象
     * @return 操作结果
     */
    @SaCheckPermission("sch:employeeSchedule:edit")
    @Log(title = "员工排班明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchEmployeeScheduleBo bo) {
        return toAjax(schEmployeeScheduleService.updateByBo(bo));
    }

    /**
     * 删除员工排班明细
     * <p>权限标识：{@code sch:employeeSchedule:remove}</p>
     * <p>日志记录：title="员工排班明细"，businessType=DELETE</p>
     *
     * @param scheduleIds 排班明细ID数组
     * @return 操作结果
     */
    @SaCheckPermission("sch:employeeSchedule:remove")
    @Log(title = "员工排班明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{scheduleIds}")
    public R<Void> remove(@PathVariable Long[] scheduleIds) {
        return toAjax(schEmployeeScheduleService.deleteByIds(scheduleIds));
    }

    /**
     * 生成员工排班明细
     * <p>根据排班计划自动生成所有员工的排班明细。</p>
     * <p>权限标识：{@code sch:employeeSchedule:add}</p>
     * <p>日志记录：title="员工排班明细"，businessType=INSERT</p>
     *
     * @return 操作结果
     */
    @SaCheckPermission("sch:employeeSchedule:add")
    @Log(title = "员工排班明细", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public R<Void> generate() {
        return toAjax(schEmployeeScheduleService.generate());
    }
}
