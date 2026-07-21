package com.nexus.workflow.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.WfNform;
import com.nexus.workflow.domain.bo.WfNformBo;
import com.nexus.workflow.domain.vo.WfNformVo;
import com.nexus.workflow.service.IWfNformService;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.excel.utils.ExcelUtil;

import com.nexus.common.mybatis.core.page.TableDataInfo;

/**
 * 设计器单
 *
 * @author wanghuan
 * @date 2025-04-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/nform")
public class WfNformController extends BaseController {

    /** 设计器单业务服务，提供分页、导出、增删改等能力。 */
    private final IWfNformService wfNformService;

    /**
     * 查询设计器单列表。
     *
     * @param bo        设计器单查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含设计器单列表及分页元信息
     */
    @SaCheckPermission("workflow:nform:list")
    @GetMapping("/list")
    public TableDataInfo<WfNformVo> list(WfNformBo bo, PageQuery pageQuery) {
        return wfNformService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出设计器单列表。
     *
     * @param bo        设计器单查询条件
     * @param response  HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:nform:export")
    @Log(title = "设计器单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfNformBo bo, HttpServletResponse response) {
        List<WfNformVo> list = wfNformService.queryList(bo);
        ExcelUtil.exportExcel(list, "设计器单", WfNformVo.class, response);
    }

    /**
     * 获取设计器单详细信息。
     *
     * @param formId 主键
     * @return 设计器单详细信息
     */
    @SaCheckPermission("workflow:nform:query")
    @GetMapping("/{formId}")
    public R<WfNform> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long formId) {
        return R.ok(wfNformService.queryById(formId));
    }

    /**
     * 新增设计器单。
     *
     * @param bo 设计器单新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:nform:add")
    @Log(title = "设计器单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfNformBo bo) {
        return toAjax(wfNformService.insertByBo(bo));
    }

    /**
     * 修改设计器单。
     *
     * @param bo 设计器单修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:nform:edit")
    @Log(title = "设计器单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfNformBo bo) {
        return toAjax(wfNformService.updateByBo(bo));
    }

    /**
     * 删除设计器单。
     *
     * @param formIds 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:nform:remove")
    @Log(title = "设计器单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] formIds) {
        return toAjax(wfNformService.deleteWithValidByIds(List.of(formIds), true));
    }
}
