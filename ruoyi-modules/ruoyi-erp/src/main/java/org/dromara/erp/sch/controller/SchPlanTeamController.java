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
import org.dromara.erp.sch.domain.bo.SchPlanTeamBo;
import org.dromara.erp.sch.domain.vo.SchPlanTeamVo;
import org.dromara.erp.sch.service.ISchPlanTeamService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计划班组关联Controller
 *
 * @author nexus
 * @date 2025-12-26
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/planteam")
public class SchPlanTeamController extends BaseController {

    private final ISchPlanTeamService schPlanTeamService;

    @SaCheckPermission("sch:planteam:list")
    @GetMapping("/list")
    public TableDataInfo<SchPlanTeamVo> list(SchPlanTeamBo bo, PageQuery pageQuery) {
        return schPlanTeamService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:planteam:export")
    @Log(title = "计划班组关联", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchPlanTeamBo bo, HttpServletResponse response) {
        List<SchPlanTeamVo> list = schPlanTeamService.selectList(bo);
        ExcelUtil.exportExcel(list, "计划班组关联数据", SchPlanTeamVo.class, response);
    }

    @SaCheckPermission("sch:planteam:query")
    @GetMapping("/{recordId}")
    public R<SchPlanTeamVo> getInfo(@PathVariable Long recordId) {
        return R.ok(schPlanTeamService.selectById(recordId));
    }

    @SaCheckPermission("sch:planteam:add")
    @Log(title = "计划班组关联", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchPlanTeamBo bo) {
        return toAjax(schPlanTeamService.insertByBo(bo));
    }

    @SaCheckPermission("sch:planteam:edit")
    @Log(title = "计划班组关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchPlanTeamBo bo) {
        return toAjax(schPlanTeamService.updateByBo(bo));
    }

    @SaCheckPermission("sch:planteam:remove")
    @Log(title = "计划班组关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public R<Void> remove(@PathVariable Long[] recordIds) {
        return toAjax(schPlanTeamService.deleteByIds(recordIds));
    }
}
