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
import com.nexus.workflow.domain.bo.WfDdFlowBo;
import com.nexus.workflow.domain.vo.WfDdFlowVo;
import com.nexus.workflow.service.IWfDdFlowService;

/**
 * 钉钉流程
 *
 * @author wanghuan
 * @date 2023-11-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/ddFlow")
public class WfDdFlowController extends BaseController {

    /** 钉钉流程业务服务，提供分页、导出、增删改等能力。 */
    private final IWfDdFlowService iWfDdFlowService;

    /**
     * 查询钉钉流程列表。
     *
     * @param bo        钉钉流程查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含钉钉流程列表及分页元信息
     */
    @SaCheckPermission("workflow:ddFlow:list")
    @GetMapping("/list")
    public TableDataInfo<WfDdFlowVo> list(WfDdFlowBo bo, PageQuery pageQuery) {
        return iWfDdFlowService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出钉钉流程列表。
     *
     * @param bo        钉钉流程查询条件
     * @param response  HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:ddFlow:export")
    @Log(title = "钉钉流程", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDdFlowBo bo, HttpServletResponse response) {
        List<WfDdFlowVo> list = iWfDdFlowService.queryList(bo);
        ExcelUtil.exportExcel(list, "钉钉流程", WfDdFlowVo.class, response);
    }

    /**
     * 获取钉钉流程详细信息。
     *
     * @param id 主键
     * @return 钉钉流程详细信息
     */
    @SaCheckPermission("workflow:ddFlow:query")
    @GetMapping("/{id}")
    public R<WfDdFlowVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfDdFlowService.queryById(id));
    }

    /**
     * 新增钉钉流程。
     *
     * @param bo 钉钉流程新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:ddFlow:add")
    @Log(title = "钉钉流程", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDdFlowBo bo) {
        return toAjax(iWfDdFlowService.insertByBo(bo));
    }

    /**
     * 修改钉钉流程。
     *
     * @param bo 钉钉流程修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:ddFlow:edit")
    @Log(title = "钉钉流程", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDdFlowBo bo) {
        return toAjax(iWfDdFlowService.updateByBo(bo));
    }

    /**
     * 删除钉钉流程。
     *
     * @param ids 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:ddFlow:remove")
    @Log(title = "钉钉流程", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfDdFlowService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
