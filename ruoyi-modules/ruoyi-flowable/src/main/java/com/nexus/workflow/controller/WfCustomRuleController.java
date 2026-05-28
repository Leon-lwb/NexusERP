package com.nexus.workflow.controller;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.bo.WfCustomRuleBo;
import com.nexus.workflow.domain.vo.WfCustomRuleVo;
import com.nexus.workflow.service.IWfCustomRuleService;

import org.springframework.validation.annotation.Validated;

/**
 * 流程自定义业务规则
 *
 * @author wanghuan
 * @date 2023-11-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/customRule")
public class WfCustomRuleController extends BaseController {

    private final IWfCustomRuleService iWfCustomRuleService;

    /**
     * 查询流程自定义业务规则列表
     */
    @SaCheckPermission("workflow:customRule:list")
    @GetMapping("/list")
    public TableDataInfo<WfCustomRuleVo> list(WfCustomRuleBo bo, PageQuery pageQuery) {
        return iWfCustomRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程自定义业务规则列表
     */
    @SaCheckPermission("workflow:customRule:export")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfCustomRuleBo bo, HttpServletResponse response) {
        List<WfCustomRuleVo> list = iWfCustomRuleService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程自定义业务规则", WfCustomRuleVo.class, response);
    }

    /**
     * 获取流程自定义业务规则详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("workflow:customRule:query")
    @GetMapping("/{id}")
    public R<WfCustomRuleVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfCustomRuleService.queryById(id));
    }

    /**
     * 新增流程自定义业务规则
     */
    @SaCheckPermission("workflow:customRule:add")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfCustomRuleBo bo) {
        return toAjax(iWfCustomRuleService.insertByBo(bo));
    }

    /**
     * 修改流程自定义业务规则
     */
    @SaCheckPermission("workflow:customRule:edit")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfCustomRuleBo bo) {
        return toAjax(iWfCustomRuleService.updateByBo(bo));
    }

    /**
     * 删除流程自定义业务规则
     *
     * @param ids 主键串
     */
    @SaCheckPermission("workflow:customRule:remove")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfCustomRuleService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
