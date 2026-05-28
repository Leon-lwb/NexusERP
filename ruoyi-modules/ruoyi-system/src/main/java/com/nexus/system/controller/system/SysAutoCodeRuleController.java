package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.SysAutoCodeRule;
import com.nexus.system.domain.vo.SysAutoCodeRuleVo;
import com.nexus.system.service.ISysAutoCodeRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/autocode/rule")
public class SysAutoCodeRuleController extends BaseController {

    private final ISysAutoCodeRuleService ruleService;

    @SaCheckPermission("system:autocode:rule:list")
    @GetMapping("/list")
    public TableDataInfo<SysAutoCodeRuleVo> list(SysAutoCodeRule rule) {
        return TableDataInfo.build(ruleService.selectAutoCodeList(rule));
    }

    @SaCheckPermission("system:autocode:rule:query")
    @GetMapping("/{ruleId}")
    public R<SysAutoCodeRuleVo> getInfo(@PathVariable Long ruleId) {
        return R.ok(ruleService.findById(ruleId));
    }

    @SaCheckPermission("system:autocode:rule:add")
    @Log(title = "编码规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysAutoCodeRule rule) {
        if (!ruleService.checkRuleCodeUnique(rule)) {
            return R.fail("编码规则编号已存在");
        }
        if (!ruleService.checkRuleNameUnique(rule)) {
            return R.fail("编码规则名称已存在");
        }
        if ("N".equals(rule.getIsPadded())) {
            rule.setPaddedChar(null);
            rule.setPaddedMethod(null);
        }
        return toAjax(ruleService.insertInfo(rule));
    }

    @SaCheckPermission("system:autocode:rule:edit")
    @Log(title = "编码规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysAutoCodeRule rule) {
        if (!ruleService.checkRuleCodeUnique(rule)) {
            return R.fail("编码规则编号已存在");
        }
        if (!ruleService.checkRuleNameUnique(rule)) {
            return R.fail("编码规则名称已存在");
        }
        return toAjax(ruleService.updateInfo(rule));
    }

    @SaCheckPermission("system:autocode:rule:remove")
    @Log(title = "编码规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public R<Void> remove(@PathVariable Long[] ruleIds) {
        return toAjax(ruleService.deleteByIds(ruleIds));
    }
}
