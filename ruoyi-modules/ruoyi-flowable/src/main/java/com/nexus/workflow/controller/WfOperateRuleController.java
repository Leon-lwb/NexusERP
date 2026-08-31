package com.nexus.workflow.controller;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
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
import com.nexus.workflow.domain.vo.WfOperateRuleVo;
import com.nexus.workflow.domain.bo.WfOperateRuleBo;
import com.nexus.workflow.service.IWfOperateRuleService;

/**
 * 流程操作规则
 *
 * @author wanghuan
 * @date 2023-11-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/operateRule")
public class WfOperateRuleController extends BaseController {

    /** 流程操作规则业务服务，提供分页、导出、增删改等能力。 */
    private final IWfOperateRuleService iWfOperateRuleService;

    /**
     * 查询流程操作规则列表。
     *
     * @param bo        流程操作规则查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程操作规则列表及分页元信息
     */
    @SaCheckPermission("workflow:operateRule:list")
    @GetMapping("/list")
    public TableDataInfo<WfOperateRuleVo> list(WfOperateRuleBo bo, PageQuery pageQuery) {
        return iWfOperateRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程操作规则列表。
     *
     * @param bo        流程操作规则查询条件
     * @param response  HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:operateRule:export")
    @Log(title = "流程操作规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfOperateRuleBo bo, HttpServletResponse response) {
        List<WfOperateRuleVo> list = iWfOperateRuleService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程操作规则", WfOperateRuleVo.class, response);
    }

    /**
     * 获取流程操作规则详细信息。
     *
     * @param id 主键
     * @return 流程操作规则详细信息
     */
    @SaCheckPermission("workflow:operateRule:query")
    @GetMapping("/{id}")
    public R<WfOperateRuleVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfOperateRuleService.queryById(id));
    }

    /**
     * 新增流程操作规则。
     *
     * @param bo 流程操作规则新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:operateRule:add")
    @Log(title = "流程操作规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfOperateRuleBo bo) {
        return toAjax(iWfOperateRuleService.insertByBo(bo));
    }

    /**
     * 修改流程操作规则。
     *
     * @param bo 流程操作规则修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:operateRule:edit")
    @Log(title = "流程操作规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfOperateRuleBo bo) {
        return toAjax(iWfOperateRuleService.updateByBo(bo));
    }

    /**
     * 删除流程操作规则。
     *
     * @param ids 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:operateRule:remove")
    @Log(title = "流程操作规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfOperateRuleService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
