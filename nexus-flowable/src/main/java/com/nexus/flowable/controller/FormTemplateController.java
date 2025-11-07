package com.nexus.flowable.controller;

import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.flowable.domain.FormTemplate;
import com.nexus.flowable.service.IFormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 单模板Controller
 * 
 * @author ruoyi
 * @date 2025-05-12
 */
@RestController
@RequestMapping("/form/template")
@Tag(name = "【单模板】管理")
public class FormTemplateController extends BaseController
{
    @Autowired
    private IFormTemplateService formTemplateService;

    /**
     * 查询单模板列表
     */
    @Operation(summary = "查询单模板列表")
    @PreAuthorize("@ss.hasPermi('form:template:list')")
    @GetMapping("/list")
    public TableDataInfo list(FormTemplate formTemplate)
    {
        startPage();
        List<FormTemplate> list = formTemplateService.selectFormTemplateList(formTemplate);
        return getDataTable(list);
    }

    /**
     * 导出单模板列表
     */
    @Operation(summary = "导出单模板列表")
    @PreAuthorize("@ss.hasPermi('form:template:export')")
    @Log(title = "单模板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, FormTemplate formTemplate)
    {
        List<FormTemplate> list = formTemplateService.selectFormTemplateList(formTemplate);
        ExcelUtil<FormTemplate> util = new ExcelUtil<FormTemplate>(FormTemplate.class);
        util.exportExcel(response, list, "单模板数据");
    }

    /**
     * 获取单模板详细信息
     */
    @Operation(summary = "获取单模板详细信息")
    @PreAuthorize("@ss.hasPermi('form:template:query')")
    @GetMapping(value = "/{formId}")
    public AjaxResult getInfo(@PathVariable("formId") Long formId)
    {
        return success(formTemplateService.selectFormTemplateByFormId(formId));
    }

    /**
     * 新增单模板
     */
    @Operation(summary = "新增单模板")
    @PreAuthorize("@ss.hasPermi('form:template:add')")
    @Log(title = "单模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody FormTemplate formTemplate)
    {
        return toAjax(formTemplateService.insertFormTemplate(formTemplate));
    }

    /**
     * 修改单模板
     */
    @Operation(summary = "修改单模板")
    @PreAuthorize("@ss.hasPermi('form:template:edit')")
    @Log(title = "单模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody FormTemplate formTemplate)
    {
        return toAjax(formTemplateService.updateFormTemplate(formTemplate));
    }

    /**
     * 删除单模板
     */
    @Operation(summary = "删除单模板")
    @PreAuthorize("@ss.hasPermi('form:template:remove')")
    @Log(title = "单模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formIds}")
    public AjaxResult remove(@PathVariable( name = "formIds" ) Long[] formIds) 
    {
        return toAjax(formTemplateService.deleteFormTemplateByFormIds(formIds));
    }
}
