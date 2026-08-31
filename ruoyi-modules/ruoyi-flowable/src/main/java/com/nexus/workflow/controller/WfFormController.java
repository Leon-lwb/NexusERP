package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.validate.QueryGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.WfDeployForm;
import com.nexus.workflow.domain.bo.WfFormBo;
import com.nexus.workflow.domain.vo.WfFormExportVo;
import com.nexus.workflow.domain.vo.WfFormVo;
import com.nexus.workflow.service.IWfDeployFormService;
import com.nexus.workflow.service.IWfFormService;

import java.util.Arrays;
import java.util.List;

/**
 * 流程表单Controller
 * <p>提供流程表单的增删改查、导出及流程表单挂载等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/7 22:07
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/form")
public class WfFormController extends BaseController {

    /** 流程表单业务服务，提供表单分页、导出、增删改等能力。 */
    private final IWfFormService formService;

    /** 部署表单关联服务，用于挂载流程与表单的关联关系。 */
    private final IWfDeployFormService deployFormService;

    /**
     * 查询流程表单列表。
     *
     * @param bo        流程表单查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程表单列表及分页元信息
     */
    @SaCheckPermission("workflow:form:list")
    @GetMapping("/list")
    public TableDataInfo<WfFormVo> list(@Validated(QueryGroup.class) WfFormBo bo, PageQuery pageQuery) {
        return formService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程表单列表。
     *
     * @param bo       流程表单查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:form:export")
    @Log(title = "流程表单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@Validated WfFormBo bo, HttpServletResponse response) {
        List<WfFormVo> list = formService.queryList(bo);
        List<WfFormExportVo> listVo = MapstructUtils.convert(list, WfFormExportVo.class);
        ExcelUtil.exportExcel(listVo, "流程表单", WfFormExportVo.class, response);
    }

    /**
     * 获取流程表单详细信息。
     *
     * @param formId 主键
     * @return 流程表单详细信息
     */
    @SaCheckPermission("workflow:form:query")
    @GetMapping(value = "/{formId}")
    public R<WfFormVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("formId") Long formId) {
        return R.ok(formService.queryById(formId));
    }

    /**
     * 新增流程表单。
     *
     * @param bo 流程表单新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:form:add")
    @Log(title = "流程表单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody WfFormBo bo) {
        return toAjax(formService.insertForm(bo));
    }

    /**
     * 修改流程表单。
     *
     * @param bo 流程表单修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:form:edit")
    @Log(title = "流程表单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody WfFormBo bo) {
        return toAjax(formService.updateForm(bo));
    }

    /**
     * 删除流程表单。
     *
     * @param formIds 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:form:remove")
    @Log(title = "流程表单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] formIds) {
        return toAjax(formService.deleteWithValidByIds(Arrays.asList(formIds)) ? 1 : 0);
    }


    /**
     * 挂载流程表单（建立流程部署与表单的关联）。
     *
     * @param deployForm 部署表单关联对象
     * @return 操作结果，成功时返回成功状态
     */
    @Log(title = "流程表单", businessType = BusinessType.INSERT)
    @PostMapping("/addDeployForm")
    public R<Void> addDeployForm(@RequestBody WfDeployForm deployForm) {
        return toAjax(deployFormService.insertWfDeployForm(deployForm));
    }
}
