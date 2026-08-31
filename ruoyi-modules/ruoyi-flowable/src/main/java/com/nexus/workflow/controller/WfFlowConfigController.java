package com.nexus.workflow.controller;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
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
import com.nexus.workflow.domain.vo.WfFlowConfigVo;
import com.nexus.workflow.domain.vo.WfRuleVo;
import com.nexus.workflow.domain.bo.WfFlowConfigBo;
import com.nexus.workflow.service.IWfFlowConfigService;


/**
 * 流程配置
 * <p>提供流程配置主表及关联规则子表的增删改查、导出等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @date 2023-11-19
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/flowConfig")
public class WfFlowConfigController extends BaseController {

    /** 流程配置业务服务，提供配置查询、增删改及子表规则维护等能力。 */
    private final IWfFlowConfigService iWfFlowConfigService;

    /**
     * 查询流程配置列表。
     *
     * @param bo       流程配置查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程配置列表及分页元信息
     */
    @SaCheckPermission("workflow:flowConfig:list")
    @GetMapping("/list")
    public TableDataInfo<WfFlowConfigVo> list(WfFlowConfigBo bo, PageQuery pageQuery) {
        return iWfFlowConfigService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程配置列表。
     *
     * @param bo       流程配置查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:flowConfig:export")
    @Log(title = "流程配置主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfFlowConfigBo bo, HttpServletResponse response) {
        List<WfFlowConfigVo> list = iWfFlowConfigService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程配置主", WfFlowConfigVo.class, response);
    }

    /**
     * 获取流程配置详细信息。
     *
     * @param id 主键
     * @return 流程配置详细信息
     */
    @SaCheckPermission("workflow:flowConfig:query")
    @GetMapping("/{id}")
    public R<WfFlowConfigVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfFlowConfigService.queryById(id));
    }

    /**
     * 根据 configId 获取流程配置的两个子表数据。
     *
     * @param bo 流程配置查询条件（含 configId）
     * @return 流程配置及关联规则子表数据
     */
    @SaCheckPermission("workflow:flowConfig:query")
    @GetMapping("/getConfigRule")
    public R<WfRuleVo> getConfigRule(WfFlowConfigBo bo) {
        return R.ok(iWfFlowConfigService.queryConfigRule(bo));
    }

    /**
     * 修改流程配置的两个子表数据。
     *
     * @param vo 流程配置规则数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:flowConfig:edit")
    @Log(title = "更新流程配置两个子表", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/editConfigRule")
    public R<Void> editConfigRule(@RequestBody WfRuleVo vo) {
        return toAjax(iWfFlowConfigService.updateConfigRule(vo));
    }

    /**
     * 新增流程配置。
     *
     * @param bo 流程配置新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:flowConfig:add")
    @Log(title = "流程配置主", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfFlowConfigBo bo) {
        return toAjax(iWfFlowConfigService.insertByBo(bo));
    }

    /**
     * 修改流程配置。
     *
     * @param bo 流程配置修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:flowConfig:edit")
    @Log(title = "流程配置主", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfFlowConfigBo bo) {
        return toAjax(iWfFlowConfigService.updateByBo(bo));
    }

    /**
     * 删除流程配置。
     *
     * @param ids 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:flowConfig:remove")
    @Log(title = "流程配置主", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfFlowConfigService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
