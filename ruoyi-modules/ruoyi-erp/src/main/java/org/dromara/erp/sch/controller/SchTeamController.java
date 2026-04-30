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
import org.dromara.erp.sch.domain.bo.SchTeamBo;
import org.dromara.erp.sch.domain.vo.SchTeamVo;
import org.dromara.erp.sch.service.ISchTeamService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班组设置Controller
 *
 * @author nexus
 * @date 2025-12-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/team")
public class SchTeamController extends BaseController {

    private final ISchTeamService schTeamService;

    @SaCheckPermission("sch:team:list")
    @GetMapping("/list")
    public TableDataInfo<SchTeamVo> list(SchTeamBo bo, PageQuery pageQuery) {
        return schTeamService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:team:export")
    @Log(title = "班组设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchTeamBo bo, HttpServletResponse response) {
        List<SchTeamVo> list = schTeamService.selectList(bo);
        ExcelUtil.exportExcel(list, "班组设置数据", SchTeamVo.class, response);
    }

    @SaCheckPermission("sch:team:query")
    @GetMapping("/{teamId}")
    public R<SchTeamVo> getInfo(@PathVariable Long teamId) {
        return R.ok(schTeamService.selectById(teamId));
    }

    @SaCheckPermission("sch:team:add")
    @Log(title = "班组设置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchTeamBo bo) {
        return toAjax(schTeamService.insertByBo(bo));
    }

    @SaCheckPermission("sch:team:edit")
    @Log(title = "班组设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchTeamBo bo) {
        return toAjax(schTeamService.updateByBo(bo));
    }

    @SaCheckPermission("sch:team:remove")
    @Log(title = "班组设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamIds}")
    public R<Void> remove(@PathVariable Long[] teamIds) {
        return toAjax(schTeamService.deleteByIds(teamIds));
    }
}
