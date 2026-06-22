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

/**
 * 编码规则控制器
 * <p>管理自动编码规则（sys_auto_code_rule）的增删改查，
 * 规则定义了编码的最大长度、补齐策略等元信息。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/autocode/rule")
public class SysAutoCodeRuleController extends BaseController {

    private final ISysAutoCodeRuleService ruleService;

    /**
     * 分页列表查询
     *
     * @param rule 查询条件
     * @return 编码规则分页列表
     */
    @SaCheckPermission("system:autocode:rule:list")
    @GetMapping("/list")
    public TableDataInfo<SysAutoCodeRuleVo> list(SysAutoCodeRule rule) {
        return TableDataInfo.build(ruleService.selectAutoCodeList(rule));
    }

    /**
     * 根据 ID 查询编码规则详情
     *
     * @param ruleId 规则 ID
     * @return 详情
     */
    @SaCheckPermission("system:autocode:rule:query")
    @GetMapping("/{ruleId}")
    public R<SysAutoCodeRuleVo> getInfo(@PathVariable Long ruleId) {
        return R.ok(ruleService.findById(ruleId));
    }

    /**
     * 新增编码规则
     *
     * @param rule 编码规则实体
     * @return 结果
     */
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

    /**
     * 修改编码规则
     *
     * @param rule 编码规则实体
     * @return 结果
     */
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

    /**
     * 批量删除编码规则
     *
     * @param ruleIds 规则 ID 数组
     * @return 结果
     */
    @SaCheckPermission("system:autocode:rule:remove")
    @Log(title = "编码规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public R<Void> remove(@PathVariable Long[] ruleIds) {
        return toAjax(ruleService.deleteByIds(ruleIds));
    }
}
