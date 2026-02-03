package com.nexus.workflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.QueryGroup;
import com.nexus.common.utils.MapstructUtils;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.annotation.Log;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.core.controller.BaseController;
import com.nexus.workflow.domain.WfDeployForm;
import com.nexus.workflow.domain.bo.WfFormBo;
import com.nexus.workflow.domain.vo.WfFormExportVo;
import com.nexus.workflow.domain.vo.WfFormVo;
import com.nexus.workflow.service.IWfDeployFormService;
import com.nexus.workflow.service.IWfFormService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 流程表单Controller
 *
 * @author nbacheng
 * @createTime 2022/3/7 22:07
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/form")
public class WfFormController extends BaseController {

    private final IWfFormService formService;

    private final IWfDeployFormService deployFormService;

    /**
     * 查询流程表单列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:form:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfFormVo> list(@Validated(QueryGroup.class) WfFormBo bo, PageQuery pageQuery) {
        return formService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程表单列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:form:export')")
    @Log(title = "流程表单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@Validated WfFormBo bo, HttpServletResponse response) {
        List<WfFormVo> list = formService.queryList(bo);
        List<WfFormExportVo> listVo = MapstructUtils.convert(list, WfFormExportVo.class);
        ExcelUtil<WfFormExportVo> util = new ExcelUtil<>(WfFormExportVo.class);
        util.exportExcel(response, listVo, "流程表单", "流程表单");
    }

    /**
     * 获取流程表单详细信息
     * @param formId 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:form:query')")
    @GetMapping(value = "/{formId}")
    public R<WfFormVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("formId") Long formId) {
        return R.ok(formService.queryById(formId));
    }

    /**
     * 新增流程表单
     */
    @PreAuthorize("@ss.hasPermi('workflow:form:add')")
    @Log(title = "流程表单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody WfFormBo bo) {
        return formService.insertForm(bo) > 0 ? R.ok() : R.fail();
    }

    /**
     * 修改流程表单
     */
    @PreAuthorize("@ss.hasPermi('workflow:form:edit')")
    @Log(title = "流程表单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody WfFormBo bo) {
        return formService.updateForm(bo) > 0 ? R.ok() : R.fail();
    }

    /**
     * 删除流程表单
     * @param formIds 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:form:remove')")
    @Log(title = "流程表单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] formIds) {
        return formService.deleteWithValidByIds(Arrays.asList(formIds)) ? R.ok() : R.fail();
    }


    /**
     * 挂载流程表单
     */
    @Log(title = "流程表单", businessType = BusinessType.INSERT)
    @PostMapping("/addDeployForm")
    public R<Void> addDeployForm(@RequestBody WfDeployForm deployForm) {
        return deployFormService.insertWfDeployForm(deployForm) > 0 ? R.ok() : R.fail();
    }
}
