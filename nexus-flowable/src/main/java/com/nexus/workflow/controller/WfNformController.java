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
import com.nexus.workflow.domain.WfNform;
import com.nexus.workflow.domain.bo.WfNformBo;
import com.nexus.workflow.domain.vo.WfNformVo;
import com.nexus.workflow.service.IWfNformService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设计器单
 *
 * @author nbacheng
 * @date 2025-04-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/nform")
public class WfNformController extends BaseController {

    private final IWfNformService wfNformService;

    /**
     * 查询设计器单列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfNformVo> list(WfNformBo bo, PageQuery pageQuery) {
        return wfNformService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程表单列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:export')")
    @Log(title = "流程表单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfNformBo bo, HttpServletResponse response) {
        List<WfNformVo> list = wfNformService.queryList(bo);
        ExcelUtil<WfNformVo> util = new ExcelUtil<>(WfNformVo.class);
        util.exportExcel(response, list, "流程表单", "流程表单");
    }

    /**
     * 获取设计器单详细信息
     *
     * @param formId 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:query')")
    @GetMapping("/{formId}")
    public R<WfNform> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long formId) {
        return R.ok(wfNformService.queryById(formId));
    }

    /**
     * 新增设计器单
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:add')")
    @Log(title = "设计器单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfNformBo bo) {
        return wfNformService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改设计器单
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @Log(title = "设计器单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfNformBo bo) {
        return wfNformService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除设计器单
     *
     * @param formIds 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:remove')")
    @Log(title = "设计器单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] formIds) {
        return wfNformService.deleteWithValidByIds(List.of(formIds), true) ? R.ok() : R.fail();
    }
}
