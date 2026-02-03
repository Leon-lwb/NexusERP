package com.nexus.workflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.annotation.RepeatSubmit;
import com.nexus.common.annotation.Log;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.core.controller.BaseController;
import com.nexus.workflow.domain.bo.WfOperateRuleBo;
import com.nexus.workflow.domain.vo.WfOperateRuleVo;
import com.nexus.workflow.service.IWfOperateRuleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 流程操作规则
 *
 * @author nbacheng
 * @date 2023-11-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/operateRule")
public class WfOperateRuleController extends BaseController {

    private final IWfOperateRuleService iWfOperateRuleService;

    /**
     * 查询流程操作规则列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:operateRule:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfOperateRuleVo> list(WfOperateRuleBo bo, PageQuery pageQuery) {
        return iWfOperateRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程操作规则列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:operateRule:export')")
    @Log(title = "流程操作规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfOperateRuleBo bo, HttpServletResponse response) {
        List<WfOperateRuleVo> list = iWfOperateRuleService.queryList(bo);
        ExcelUtil<WfOperateRuleVo> util = new ExcelUtil<>(WfOperateRuleVo.class);
        util.exportExcel(response, list, "流程操作规则", "流程操作规则");
    }

    /**
     * 获取流程操作规则详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:operateRule:query')")
    @GetMapping("/{id}")
    public R<WfOperateRuleVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfOperateRuleService.queryById(id));
    }

    /**
     * 新增流程操作规则
     */
    @PreAuthorize("@ss.hasPermi('workflow:operateRule:add')")
    @Log(title = "流程操作规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfOperateRuleBo bo) {
        return iWfOperateRuleService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改流程操作规则
     */
    @PreAuthorize("@ss.hasPermi('workflow:operateRule:edit')")
    @Log(title = "流程操作规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfOperateRuleBo bo) {
        return iWfOperateRuleService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除流程操作规则
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:operateRule:remove')")
    @Log(title = "流程操作规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfOperateRuleService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
