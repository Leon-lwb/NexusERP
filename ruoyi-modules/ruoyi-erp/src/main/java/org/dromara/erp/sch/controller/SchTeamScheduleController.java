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
import org.dromara.erp.sch.domain.bo.SchTeamScheduleBo;
import org.dromara.erp.sch.domain.vo.SchTeamScheduleVo;
import org.dromara.erp.sch.service.ISchTeamScheduleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班组排班（模板）Controller
 *
 * @author nexus
 * @date 2025-12-30
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/teamSchedule")
public class SchTeamScheduleController extends BaseController {

    private final ISchTeamScheduleService schTeamScheduleService;

    @SaCheckPermission("sch:teamSchedule:list")
    @GetMapping("/list")
    public TableDataInfo<SchTeamScheduleVo> list(SchTeamScheduleBo bo, PageQuery pageQuery) {
        return schTeamScheduleService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:teamSchedule:export")
    @Log(title = "班组排班（模板）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchTeamScheduleBo bo, HttpServletResponse response) {
        List<SchTeamScheduleVo> list = schTeamScheduleService.selectList(bo);
        ExcelUtil.exportExcel(list, "班组排班（模板）数据", SchTeamScheduleVo.class, response);
    }

    @SaCheckPermission("sch:teamSchedule:query")
    @GetMapping("/{teamScheduleId}")
    public R<SchTeamScheduleVo> getInfo(@PathVariable Long teamScheduleId) {
        return R.ok(schTeamScheduleService.selectById(teamScheduleId));
    }

    @SaCheckPermission("sch:teamSchedule:add")
    @Log(title = "班组排班（模板）", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchTeamScheduleBo bo) {
        return toAjax(schTeamScheduleService.insertByBo(bo));
    }

    @SaCheckPermission("sch:teamSchedule:edit")
    @Log(title = "班组排班（模板）", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchTeamScheduleBo bo) {
        return toAjax(schTeamScheduleService.updateByBo(bo));
    }

    @SaCheckPermission("sch:teamSchedule:remove")
    @Log(title = "班组排班（模板）", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamScheduleIds}")
    public R<Void> remove(@PathVariable Long[] teamScheduleIds) {
        return toAjax(schTeamScheduleService.deleteByIds(teamScheduleIds));
    }
}
