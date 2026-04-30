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
import org.dromara.erp.sch.domain.bo.SchPunchRuleBo;
import org.dromara.erp.sch.domain.vo.SchPunchRuleVo;
import org.dromara.erp.sch.service.ISchPunchRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打卡规则Controller
 *
 * @author nexus
 * @date 2026-01-07
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/punchRule")
public class SchPunchRuleController extends BaseController {

    private final ISchPunchRuleService schPunchRuleService;

    @SaCheckPermission("sch:punchRule:list")
    @GetMapping("/list")
    public TableDataInfo<SchPunchRuleVo> list(SchPunchRuleBo bo, PageQuery pageQuery) {
        return schPunchRuleService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:punchRule:export")
    @Log(title = "打卡规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchPunchRuleBo bo, HttpServletResponse response) {
        List<SchPunchRuleVo> list = schPunchRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "打卡规则数据", SchPunchRuleVo.class, response);
    }

    @SaCheckPermission("sch:punchRule:query")
    @GetMapping("/{ruleId}")
    public R<SchPunchRuleVo> getInfo(@PathVariable Long ruleId) {
        return R.ok(schPunchRuleService.selectById(ruleId));
    }

    @SaCheckPermission("sch:punchRule:add")
    @Log(title = "打卡规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchPunchRuleBo bo) {
        return toAjax(schPunchRuleService.insertByBo(bo));
    }

    @SaCheckPermission("sch:punchRule:edit")
    @Log(title = "打卡规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchPunchRuleBo bo) {
        return toAjax(schPunchRuleService.updateByBo(bo));
    }

    @SaCheckPermission("sch:punchRule:remove")
    @Log(title = "打卡规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public R<Void> remove(@PathVariable Long[] ruleIds) {
        return toAjax(schPunchRuleService.deleteByIds(ruleIds));
    }
}
