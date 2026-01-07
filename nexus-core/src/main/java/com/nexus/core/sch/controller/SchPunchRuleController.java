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
import com.nexus.core.sch.domain.SchPunchRule;
import com.nexus.core.sch.service.ISchPunchRuleService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 打卡规则Controller
 *
 * @author nexus
 * @date 2026-01-07
 */
@Api(tags = "打卡规则Controller")
@RestController
@RequestMapping("/sch/punchRule")
public class SchPunchRuleController extends BaseController
{
    @Autowired
    private ISchPunchRuleService schPunchRuleService;

    /**
     * 查询打卡规则列表
     */
    @ApiOperation("查询打卡规则列表")
    @PreAuthorize("@ss.hasPermi('sch:punchRule:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchPunchRule schPunchRule)
    {
        startPage();
        List<SchPunchRule> list = schPunchRuleService.selectSchPunchRuleList(schPunchRule);
        return getDataTable(list);
    }

    /**
     * 导出打卡规则列表
     */
    @ApiOperation("导出打卡规则列表")
    @PreAuthorize("@ss.hasPermi('sch:punchRule:export')")
    @Log(title = "打卡规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchPunchRule schPunchRule)
    {
        List<SchPunchRule> list = schPunchRuleService.selectSchPunchRuleList(schPunchRule);
        ExcelUtil<SchPunchRule> util = new ExcelUtil<SchPunchRule>(SchPunchRule.class);
        util.exportExcel(response, list, "打卡规则数据");
    }

    /**
     * 获取打卡规则详细信息
     */
    @ApiOperation("获取打卡规则列表")
    @PreAuthorize("@ss.hasPermi('sch:punchRule:query')")
    @GetMapping(value = "/{ruleId}")
    public AjaxResult getInfo(@PathVariable("ruleId") Long ruleId)
    {
        return success(schPunchRuleService.selectSchPunchRuleByRuleId(ruleId));
    }

    /**
     * 新增打卡规则
     */
    @ApiOperation("新增打卡规则详细信息")
    @PreAuthorize("@ss.hasPermi('sch:punchRule:add')")
    @Log(title = "打卡规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchPunchRule schPunchRule)
    {
        return toAjax(schPunchRuleService.insertSchPunchRule(schPunchRule));
    }

    /**
     * 修改打卡规则
     */
    @ApiOperation("修改打卡规则详细信息")
    @PreAuthorize("@ss.hasPermi('sch:punchRule:edit')")
    @Log(title = "打卡规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchPunchRule schPunchRule)
    {
        return toAjax(schPunchRuleService.updateSchPunchRule(schPunchRule));
    }

    /**
     * 删除打卡规则
     */
    @ApiOperation("删除打卡规则信息")
    @PreAuthorize("@ss.hasPermi('sch:punchRule:remove')")
    @Log(title = "打卡规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds)
    {
        return toAjax(schPunchRuleService.deleteSchPunchRuleByRuleIds(ruleIds));
    }
}
