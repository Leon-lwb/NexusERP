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
import com.nexus.workflow.domain.vo.WfDefaultOperateVo;
import com.nexus.workflow.domain.bo.WfDefaultOperateBo;
import com.nexus.workflow.service.IWfDefaultOperateService;

/**
 * 流程默认操作
 *
 * @author wanghuan
 * @date 2023-11-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/defaultOperate")
public class WfDefaultOperateController extends BaseController {

    /** 流程默认操作业务服务，提供分页、导出、增删改等能力。 */
    private final IWfDefaultOperateService iWfDefaultOperateService;

    /**
     * 查询流程默认操作列表。
     *
     * @param bo        流程默认操作查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程默认操作列表及分页元信息
     */
    @SaCheckPermission("workflow:defaultOperate:list")
    @GetMapping("/list")
    public TableDataInfo<WfDefaultOperateVo> list(WfDefaultOperateBo bo, PageQuery pageQuery) {
        return iWfDefaultOperateService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程默认操作列表。
     *
     * @param bo        流程默认操作查询条件
     * @param response  HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:defaultOperate:export")
    @Log(title = "流程默认操作", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDefaultOperateBo bo, HttpServletResponse response) {
        List<WfDefaultOperateVo> list = iWfDefaultOperateService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程默认操作", WfDefaultOperateVo.class, response);
    }

    /**
     * 获取流程默认操作详细信息。
     *
     * @param id 主键
     * @return 流程默认操作详细信息
     */
    @SaCheckPermission("workflow:defaultOperate:query")
    @GetMapping("/{id}")
    public R<WfDefaultOperateVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfDefaultOperateService.queryById(id));
    }

    /**
     * 新增流程默认操作。
     *
     * @param bo 流程默认操作新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:defaultOperate:add")
    @Log(title = "流程默认操作", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDefaultOperateBo bo) {
        return toAjax(iWfDefaultOperateService.insertByBo(bo));
    }

    /**
     * 修改流程默认操作。
     *
     * @param bo 流程默认操作修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:defaultOperate:edit")
    @Log(title = "流程默认操作", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDefaultOperateBo bo) {
        return toAjax(iWfDefaultOperateService.updateByBo(bo));
    }

    /**
     * 删除流程默认操作。
     *
     * @param ids 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:defaultOperate:remove")
    @Log(title = "流程默认操作", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfDefaultOperateService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
