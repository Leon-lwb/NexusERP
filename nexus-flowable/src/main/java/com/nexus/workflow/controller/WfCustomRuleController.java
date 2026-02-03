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
import com.nexus.workflow.domain.bo.WfCustomRuleBo;
import com.nexus.workflow.domain.vo.WfCustomRuleVo;
import com.nexus.workflow.service.IWfCustomRuleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 流程自定义业务规则
 *
 * @author nbacheng
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
    @PreAuthorize("@ss.hasPermi('workflow:customRule:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfCustomRuleVo> list(WfCustomRuleBo bo, PageQuery pageQuery) {
        return iWfCustomRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程自定义业务规则列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:customRule:export')")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfCustomRuleBo bo, HttpServletResponse response) {
        List<WfCustomRuleVo> list = iWfCustomRuleService.queryList(bo);
        ExcelUtil<WfCustomRuleVo> util = new ExcelUtil<WfCustomRuleVo>(WfCustomRuleVo.class);
        util.exportExcel(response, list, "流程自定义业务规则", "流程自定义业务规则");
    }

    /**
     * 获取流程自定义业务规则详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:customRule:query')")
    @GetMapping("/{id}")
    public R<WfCustomRuleVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfCustomRuleService.queryById(id));
    }

    /**
     * 新增流程自定义业务规则
     */
    @PreAuthorize("@ss.hasPermi('workflow:customRule:add')")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfCustomRuleBo bo) {
        return iWfCustomRuleService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改流程自定义业务规则
     */
    @PreAuthorize("@ss.hasPermi('workflow:customRule:edit')")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfCustomRuleBo bo) {
        return iWfCustomRuleService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除流程自定义业务规则
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:customRule:remove')")
    @Log(title = "流程自定义业务规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfCustomRuleService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
