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
import com.nexus.workflow.domain.bo.WfFlowConfigBo;
import com.nexus.workflow.domain.vo.WfFlowConfigVo;
import com.nexus.workflow.domain.vo.WfRuleVo;
import com.nexus.workflow.service.IWfFlowConfigService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;


/**
 * 流程配置
 *
 * @author nbacheng
 * @date 2023-11-19
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/flowConfig")
public class WfFlowConfigController extends BaseController {

    private final IWfFlowConfigService iWfFlowConfigService;

    /**
     * 查询流程配置列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfFlowConfigVo> list(WfFlowConfigBo bo, PageQuery pageQuery) {
        return iWfFlowConfigService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程配置列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:export')")
    @Log(title = "流程配置主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfFlowConfigBo bo, HttpServletResponse response) {
        List<WfFlowConfigVo> list = iWfFlowConfigService.queryList(bo);
        ExcelUtil<WfFlowConfigVo> util = new ExcelUtil<>(WfFlowConfigVo.class);
        util.exportExcel(response, list, "流程配置主", "流程配置主");
    }

    /**
     * 获取流程配置详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:query')")
    @GetMapping("/{id}")
    public R<WfFlowConfigVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfFlowConfigService.queryById(id));
    }
    
    /**
     * 根据configId获取流程配置两个子表
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:query')")
    @GetMapping("/getConfigRule")
    public R<WfRuleVo> getConfigRule(WfFlowConfigBo bo) {
        return R.ok(iWfFlowConfigService.queryConfigRule(bo));
    }
    
    /**
     * 修改流程配置两个子表
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:edit')")
    @Log(title = "更新流程配置两个子表", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/editConfigRule")
    public R<Void> editConfigRule(@RequestBody WfRuleVo vo) {
        return iWfFlowConfigService.updateConfigRule(vo) ? R.ok() : R.fail();
    }

    /**
     * 新增流程配置
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:add')")
    @Log(title = "流程配置主", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfFlowConfigBo bo) {
        return iWfFlowConfigService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改流程配置
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:edit')")
    @Log(title = "流程配置主", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfFlowConfigBo bo) {
        return iWfFlowConfigService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除流程配置
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:flowConfig:remove')")
    @Log(title = "流程配置主", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfFlowConfigService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
