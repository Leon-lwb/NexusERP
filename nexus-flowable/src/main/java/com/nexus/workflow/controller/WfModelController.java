package com.nexus.workflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.utils.MapstructUtils;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.annotation.RepeatSubmit;
import com.nexus.common.annotation.Log;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.core.controller.BaseController;
import com.nexus.workflow.domain.bo.WfCategoryBo;
import com.nexus.workflow.domain.bo.WfModelBo;
import com.nexus.workflow.domain.vo.WfCategoryVo;
import com.nexus.workflow.domain.vo.WfModelExportVo;
import com.nexus.workflow.domain.vo.WfModelVo;
import com.nexus.workflow.service.IWfCategoryService;
import com.nexus.workflow.service.IWfModelService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流流程模型管理
 *
 * @author nbacheng
 * @createTime 2022/6/21 9:09
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/model")
public class WfModelController extends BaseController {

    private final IWfModelService modelService;
    private final IWfCategoryService categoryService;

    /**
     * 查询流程模型列表
     *
     * @param modelBo 流程模型对象
     * @param pageQuery 分页参数
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfModelVo> list(WfModelBo modelBo, PageQuery pageQuery) {
        return modelService.list(modelBo, pageQuery);
    }

    /**
     * 查询流程模型列表
     *
     * @param modelBo 流程模型对象
     * @param pageQuery 分页参数
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:list')")
    @GetMapping("/historyList")
    public WFTableDataInfo<WfModelVo> historyList(WfModelBo modelBo, PageQuery pageQuery) {
        return modelService.historyList(modelBo, pageQuery);
    }

    /**
     * 获取流程模型详细信息
     *
     * @param modelId 模型主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:query')")
    @GetMapping(value = "/{modelId}")
    public R<WfModelVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("modelId") String modelId) {
        return R.ok(modelService.getModel(modelId));
    }

    /**
     * 获取流程表单详细信息
     *
     * @param modelId 模型主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:query')")
    @GetMapping(value = "/bpmnXml/{modelId}")
    public R<String> getBpmnXml(@NotNull(message = "主键不能为空") @PathVariable("modelId") String modelId) {
        return R.ok(modelService.queryBpmnXmlById(modelId),"操作成功");
    }

    /**
     * 新增流程模型
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:add')")
    @Log(title = "流程模型", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfModelBo modelBo) {
        modelService.insertModel(modelBo);
        return R.ok();
    }

    /**
     * 修改流程模型
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:edit')")
    @Log(title = "流程模型", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfModelBo modelBo) {
        modelService.updateModel(modelBo);
        return R.ok();
    }

    /**
     * 保存流程模型
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:save')")
    @Log(title = "保存流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/save")
    public R<String> save(@RequestBody WfModelBo modelBo) {
        modelService.saveModel(modelBo);
        return R.ok();
    }

    /**
     * 设为最新流程模型
     * @param modelId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:save')")
    @Log(title = "设为最新流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/latest")
    public R<?> latest(@RequestParam String modelId) {
        modelService.latestModel(modelId);
        return R.ok();
    }

    /**
     * 删除流程模型
     *
     * @param modelIds 流程模型主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:remove')")
    @Log(title = "删除流程模型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelIds}")
    public R<String> remove(@NotEmpty(message = "主键不能为空") @PathVariable String[] modelIds) {
        modelService.deleteByIds(Arrays.asList(modelIds));
        return R.ok();
    }

    /**
     * 部署流程模型
     *
     * @param modelId 流程模型主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:deploy')")
    @Log(title = "部署流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/deploy")
    public R<Void> deployModel(@RequestParam String modelId) {
        modelService.deployModel(modelId);
        return R.ok();
    }
    
    /**
     * 配置流程模型
     *
     * @param modelId 流程模型主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:model:config')")
    @Log(title = "配置流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/config/{modelId}/{appType}")
    public R<String> configModel(@PathVariable String modelId, @PathVariable String appType) {
        return R.ok(modelService.configModel(modelId, appType));
    }

    /**
     * 导出流程模型数据
     */
    @Log(title = "导出流程模型数据", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('workflow:model:export')")
    @PostMapping("/export")
    public void export(WfModelBo modelBo, HttpServletResponse response) {
        List<WfModelVo> list =  modelService.list(modelBo);
        List<WfModelExportVo> listVo = MapstructUtils.convert(list, WfModelExportVo.class);
        List<WfCategoryVo> categoryVos = categoryService.queryList(new WfCategoryBo());
        Map<String, String> categoryMap = categoryVos.stream()
            .collect(Collectors.toMap(WfCategoryVo::getCode, WfCategoryVo::getCategoryName));
        for (WfModelExportVo exportVo : listVo) {
            exportVo.setCategoryName(categoryMap.get(exportVo.getCategory()));
        }
        ExcelUtil<WfModelExportVo> util = new ExcelUtil<>(WfModelExportVo.class);
        util.exportExcel(response, listVo, "流程模型数据", "流程模型数据");
    }
}
