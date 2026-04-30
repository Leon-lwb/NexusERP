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

    @SaCheckPermission("sch:employeeSchedule:list")
    @GetMapping("/list")
    public TableDataInfo<SchEmployeeScheduleVo> list(SchEmployeeScheduleBo bo, PageQuery pageQuery) {
        return schEmployeeScheduleService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:employeeSchedule:export")
    @Log(title = "员工排班明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchEmployeeScheduleBo bo, HttpServletResponse response) {
        List<SchEmployeeScheduleVo> list = schEmployeeScheduleService.selectList(bo);
        ExcelUtil.exportExcel(list, "员工排班明细数据", SchEmployeeScheduleVo.class, response);
    }

    @SaCheckPermission("sch:employeeSchedule:query")
    @GetMapping("/{scheduleId}")
    public R<SchEmployeeScheduleVo> getInfo(@PathVariable Long scheduleId) {
        return R.ok(schEmployeeScheduleService.selectById(scheduleId));
    }

    @SaCheckPermission("sch:employeeSchedule:add")
    @Log(title = "员工排班明细", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchEmployeeScheduleBo bo) {
        return toAjax(schEmployeeScheduleService.insertByBo(bo));
    }

    @SaCheckPermission("sch:employeeSchedule:edit")
    @Log(title = "员工排班明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchEmployeeScheduleBo bo) {
        return toAjax(schEmployeeScheduleService.updateByBo(bo));
    }

    @SaCheckPermission("sch:employeeSchedule:remove")
    @Log(title = "员工排班明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{scheduleIds}")
    public R<Void> remove(@PathVariable Long[] scheduleIds) {
        return toAjax(schEmployeeScheduleService.deleteByIds(scheduleIds));
    }

    /**
     * 生成员工排班明细
     */
    @SaCheckPermission("sch:employeeSchedule:add")
    @Log(title = "员工排班明细", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public R<Void> generate() {
        return toAjax(schEmployeeScheduleService.generate());
    }
}
