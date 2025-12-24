package com.nexus.core.sch.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.core.sch.domain.SchTeam;
import com.nexus.core.sch.service.ISchTeamService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 班组设置Controller
 *
 * @author nexus
 * @date 2025-12-23
 */
@Api(tags = "班组设置Controller")
@RestController
@RequestMapping("/sch/team")
public class SchTeamController extends BaseController
{
    @Autowired
    private ISchTeamService schTeamService;

    /**
     * 查询班组设置列表
     */
    @ApiOperation("查询班组设置列表")
    @PreAuthorize("@ss.hasPermi('sch:team:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchTeam schTeam)
    {
        startPage();
        List<SchTeam> list = schTeamService.selectSchTeamList(schTeam);
        return getDataTable(list);
    }

    /**
     * 导出班组设置列表
     */
    @ApiOperation("导出班组设置列表")
    @PreAuthorize("@ss.hasPermi('sch:team:export')")
    @Log(title = "班组设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchTeam schTeam)
    {
        List<SchTeam> list = schTeamService.selectSchTeamList(schTeam);
        ExcelUtil<SchTeam> util = new ExcelUtil<SchTeam>(SchTeam.class);
        util.exportExcel(response, list, "班组设置数据");
    }

    /**
     * 获取班组设置详细信息
     */
    @ApiOperation("获取班组设置列表")
    @PreAuthorize("@ss.hasPermi('sch:team:query')")
    @GetMapping(value = "/{teamId}")
    public AjaxResult getInfo(@PathVariable("teamId") Long teamId)
    {
        return success(schTeamService.selectSchTeamByTeamId(teamId));
    }

    /**
     * 新增班组设置
     */
    @ApiOperation("新增班组设置详细信息")
    @PreAuthorize("@ss.hasPermi('sch:team:add')")
    @Log(title = "班组设置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchTeam schTeam)
    {
        return toAjax(schTeamService.insertSchTeam(schTeam));
    }

    /**
     * 修改班组设置
     */
    @ApiOperation("修改班组设置详细信息")
    @PreAuthorize("@ss.hasPermi('sch:team:edit')")
    @Log(title = "班组设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchTeam schTeam)
    {
        return toAjax(schTeamService.updateSchTeam(schTeam));
    }

    /**
     * 删除班组设置
     */
    @ApiOperation("删除班组设置信息")
    @PreAuthorize("@ss.hasPermi('sch:team:remove')")
    @Log(title = "班组设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamIds}")
    public AjaxResult remove(@PathVariable Long[] teamIds)
    {
        return toAjax(schTeamService.deleteSchTeamByTeamIds(teamIds));
    }
}
