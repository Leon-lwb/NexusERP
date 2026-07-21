package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.bo.WfCategoryBo;
import com.nexus.workflow.domain.bo.WfModelBo;
import com.nexus.workflow.domain.vo.WfCategoryVo;
import com.nexus.workflow.domain.vo.WfModelExportVo;
import com.nexus.workflow.domain.vo.WfModelVo;
import com.nexus.workflow.service.IWfCategoryService;
import com.nexus.workflow.service.IWfModelService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流流程模型管理
 * <p>提供流程模型的增删改查、历史版本、部署、配置、BPMN XML 读取及导出等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/6/21 9:09
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/model")
public class WfModelController extends BaseController {

    /** 流程模型业务服务，提供模型列表、详情、保存、部署、配置等能力。 */
    private final IWfModelService modelService;
    /** 流程分类业务服务，用于导出时映射分类编码与名称。 */
    private final IWfCategoryService categoryService;

    /**
     * 查询流程模型列表。
     *
     * @param modelBo   流程模型查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程模型列表及分页元信息
     */
    @SaCheckPermission("workflow:model:list")
    @GetMapping("/list")
    public TableDataInfo<WfModelVo> list(WfModelBo modelBo, PageQuery pageQuery) {
        return modelService.list(modelBo, pageQuery);
    }

    /**
     * 查询流程模型历史列表。
     *
     * @param modelBo   流程模型查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含历史流程模型列表及分页元信息
     */
    @SaCheckPermission("workflow:model:list")
    @GetMapping("/historyList")
    public TableDataInfo<WfModelVo> historyList(WfModelBo modelBo, PageQuery pageQuery) {
        return modelService.historyList(modelBo, pageQuery);
    }

    /**
     * 获取流程模型详细信息。
     *
     * @param modelId 模型主键
     * @return 流程模型详细信息
     */
    @SaCheckPermission("workflow:model:query")
    @GetMapping(value = "/{modelId}")
    public R<WfModelVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("modelId") String modelId) {
        return R.ok(modelService.getModel(modelId));
    }

    /**
     * 获取流程模型 BPMN XML 内容。
     *
     * @param modelId 模型主键
     * @return 流程模型 BPMN XML 字符串
     */
    @SaCheckPermission("workflow:model:query")
    @GetMapping(value = "/bpmnXml/{modelId}")
    public R<String> getBpmnXml(@NotNull(message = "主键不能为空") @PathVariable("modelId") String modelId) {
        return R.ok("操作成功", modelService.queryBpmnXmlById(modelId));
    }

    /**
     * 新增流程模型。
     *
     * @param modelBo 流程模型新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:add")
    @Log(title = "流程模型", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfModelBo modelBo) {
        modelService.insertModel(modelBo);
        return R.ok();
    }

    /**
     * 修改流程模型。
     *
     * @param modelBo 流程模型修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:edit")
    @Log(title = "流程模型", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfModelBo modelBo) {
        modelService.updateModel(modelBo);
        return R.ok();
    }

    /**
     * 保存流程模型（设计器保存模型内容与图形）。
     *
     * @param modelBo 流程模型数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:save")
    @Log(title = "保存流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/save")
    public R<String> save(@RequestBody WfModelBo modelBo) {
        modelService.saveModel(modelBo);
        return R.ok();
    }

    /**
     * 将指定流程模型设为最新版本。
     *
     * @param modelId 模型主键
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:save")
    @Log(title = "设为最新流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/latest")
    public R<?> latest(@RequestParam String modelId) {
        modelService.latestModel(modelId);
        return R.ok();
    }

    /**
     * 删除流程模型。
     *
     * @param modelIds 流程模型主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:remove")
    @Log(title = "删除流程模型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelIds}")
    public R<String> remove(@NotEmpty(message = "主键不能为空") @PathVariable String[] modelIds) {
        modelService.deleteByIds(Arrays.asList(modelIds));
        return R.ok();
    }

    /**
     * 部署（发布）流程模型。
     *
     * @param modelId 流程模型主键
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:deploy")
    @Log(title = "部署流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/deploy")
    public R<Void> deployModel(@RequestParam String modelId) {
        modelService.deployModel(modelId);
        return R.ok("发布成功!");
    }

    /**
     * 配置流程模型（绑定应用类型）。
     *
     * @param modelId 流程模型主键
     * @param appType 应用类型
     * @return 操作结果，成功时返回配置结果
     */
    @SaCheckPermission("workflow:model:config")
    @Log(title = "配置流程模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/config/{modelId}/{appType}")
    public R<Void> configModel(@PathVariable String modelId, @PathVariable String appType) {
        return R.ok(modelService.configModel(modelId, appType));
    }

    /**
     * 导出流程模型数据。
     *
     * @param modelBo  流程模型查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "导出流程模型数据", businessType = BusinessType.EXPORT)
    @SaCheckPermission("workflow:model:export")
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
        ExcelUtil.exportExcel(listVo, "流程模型数据", WfModelExportVo.class, response);
    }
}
