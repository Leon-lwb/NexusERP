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
import com.nexus.workflow.domain.bo.WfCustomFormBo;
import com.nexus.workflow.domain.vo.CustomFormVo;
import com.nexus.workflow.domain.vo.WfCustomFormVo;
import com.nexus.workflow.service.IWfCustomFormService;

import org.springframework.validation.annotation.Validated;

/**
 * 流程业务单
 *
 * @author wanghuan
 * @date 2023-10-09
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/customForm")
public class WfCustomFormController extends BaseController {

    /** 流程业务单业务服务，提供分页、导出、增删改及表单关联等能力。 */
    private final IWfCustomFormService iWfCustomFormService;

    /**
     * 查询流程业务单列表。
     *
     * @param bo        流程业务单查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程业务单列表及分页元信息
     */
    @SaCheckPermission("workflow:customForm:list")
    @GetMapping("/list")
    public TableDataInfo<WfCustomFormVo> list(WfCustomFormBo bo, PageQuery pageQuery) {
        return iWfCustomFormService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程业务单列表。
     *
     * @param bo       流程业务单查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:customForm:export")
    @Log(title = "流程业务单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfCustomFormBo bo, HttpServletResponse response) {
        List<WfCustomFormVo> list = iWfCustomFormService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程业务单", WfCustomFormVo.class, response);
    }

    /**
     * 获取流程业务单详细信息。
     *
     * @param id 主键
     * @return 流程业务单详细信息
     */
    @SaCheckPermission("workflow:customForm:query")
    @GetMapping("/{id}")
    public R<WfCustomFormVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfCustomFormService.queryById(id));
    }

    /**
     * 新增流程业务单。
     *
     * @param bo 流程业务单新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:customForm:add")
    @Log(title = "流程业务单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfCustomFormBo bo) {
        return toAjax(iWfCustomFormService.insertByBo(bo));
    }

    /**
     * 修改流程业务单。
     *
     * @param bo 流程业务单修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:customForm:edit")
    @Log(title = "流程业务单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfCustomFormBo bo) {
        return toAjax(iWfCustomFormService.updateByBo(bo));
    }

    /**
     * 关联流程业务单（将业务单与指定流程绑定）。
     *
     * @param customFormVo 业务单与流程关联信息
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:customForm:edit")
    @Log(title = "流程业务单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/updateCustom")
    public R<Void> updateCustom( @RequestBody CustomFormVo customFormVo) {
        iWfCustomFormService.updateCustom(customFormVo);
        return R.ok("关联流程成功!");
    }

    /**
     * 删除流程业务单。
     *
     * @param ids 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:customForm:remove")
    @Log(title = "流程业务单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfCustomFormService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
