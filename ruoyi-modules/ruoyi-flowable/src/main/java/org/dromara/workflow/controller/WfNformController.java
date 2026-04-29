package org.dromara.workflow.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.web.core.BaseController;
import org.dromara.workflow.domain.WfNform;
import org.dromara.workflow.domain.bo.WfNformBo;
import org.dromara.workflow.domain.vo.WfNformVo;
import org.dromara.workflow.service.IWfNformService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.excel.utils.ExcelUtil;

import org.dromara.common.mybatis.core.page.TableDataInfo;

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
    @SaCheckPermission("workflow:nform:list")
    @GetMapping("/list")
    public TableDataInfo<WfNformVo> list(WfNformBo bo, PageQuery pageQuery) {
        return wfNformService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出设计器单列表
     */
    @SaCheckPermission("workflow:nform:export")
    @Log(title = "设计器单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfNformBo bo, HttpServletResponse response) {
        List<WfNformVo> list = wfNformService.queryList(bo);
        ExcelUtil.exportExcel(list, "设计器单", WfNformVo.class, response);
    }

    /**
     * 获取设计器单详细信息
     *
     * @param formId 主键
     */
    @SaCheckPermission("workflow:nform:query")
    @GetMapping("/{formId}")
    public R<WfNform> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long formId) {
        return R.ok(wfNformService.queryById(formId));
    }

    /**
     * 新增设计器单
     */
    @SaCheckPermission("workflow:nform:add")
    @Log(title = "设计器单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfNformBo bo) {
        return toAjax(wfNformService.insertByBo(bo));
    }

    /**
     * 修改设计器单
     */
    @SaCheckPermission("workflow:nform:edit")
    @Log(title = "设计器单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfNformBo bo) {
        return toAjax(wfNformService.updateByBo(bo));
    }

    /**
     * 删除设计器单
     *
     * @param formIds 主键串
     */
    @SaCheckPermission("workflow:nform:remove")
    @Log(title = "设计器单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] formIds) {
        return toAjax(wfNformService.deleteWithValidByIds(List.of(formIds), true));
    }
}
