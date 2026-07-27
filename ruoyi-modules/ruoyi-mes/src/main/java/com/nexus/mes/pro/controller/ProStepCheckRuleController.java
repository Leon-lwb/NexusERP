package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProStepCheckRuleBo;
import com.nexus.mes.pro.domain.vo.ProStepCheckRuleVo;
import com.nexus.mes.pro.service.IProStepCheckRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * MES工序校验规则配置 Controller(P0-3)
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/step-check-rule")
public class ProStepCheckRuleController extends BaseController {

    private final IProStepCheckRuleService proStepCheckRuleService;

    /**
     * 分页查询工序校验规则列表
     */
    @SaCheckPermission("mes:pro:step-check-rule:list")
    @GetMapping("/list")
    public TableDataInfo<ProStepCheckRuleVo> list(ProStepCheckRuleBo bo, PageQuery pageQuery) {
        return proStepCheckRuleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出工序校验规则数据到Excel
     */
    @SaCheckPermission("mes:pro:step-check-rule:export")
    @Log(title = "工序校验规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProStepCheckRuleBo bo, HttpServletResponse response) {
        List<ProStepCheckRuleVo> list = proStepCheckRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "工序校验规则", ProStepCheckRuleVo.class, response);
    }

    /**
     * 根据ID获取工序校验规则详情
     */
    @SaCheckPermission("mes:pro:step-check-rule:query")
    @GetMapping("/{ruleId}")
    public R<ProStepCheckRuleVo> getInfo(@NotNull @PathVariable Long ruleId) {
        return R.ok(proStepCheckRuleService.selectById(ruleId));
    }

    /**
     * 新增工序校验规则
     */
    @SaCheckPermission("mes:pro:step-check-rule:add")
    @Log(title = "工序校验规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody ProStepCheckRuleBo bo) {
        return toAjax(proStepCheckRuleService.insertByBo(bo));
    }

    /**
     * 编辑工序校验规则
     */
    @SaCheckPermission("mes:pro:step-check-rule:edit")
    @Log(title = "工序校验规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody ProStepCheckRuleBo bo) {
        return toAjax(proStepCheckRuleService.updateByBo(bo));
    }

    /**
     * 批量删除工序校验规则
     */
    @SaCheckPermission("mes:pro:step-check-rule:remove")
    @Log(title = "工序校验规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] ruleIds) {
        return toAjax(proStepCheckRuleService.deleteByIds(Arrays.asList(ruleIds)));
    }
}
