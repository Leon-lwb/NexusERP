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
import com.nexus.workflow.domain.vo.WfMyBusinessVo;
import com.nexus.workflow.domain.bo.WfMyBusinessBo;
import com.nexus.workflow.service.IWfMyBusinessService;

/**
 * 流程业务扩展
 *
 * @author wanghuan
 * @date 2023-10-11
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/myBusiness")
public class WfMyBusinessController extends BaseController {

    /** 流程业务扩展业务服务，提供分页、导出、增删改等能力。 */
    private final IWfMyBusinessService iWfMyBusinessService;

    /**
     * 查询流程业务扩展列表。
     *
     * @param bo        流程业务扩展查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程业务扩展列表及分页元信息
     */
    @SaCheckPermission("workflow:myBusiness:list")
    @GetMapping("/list")
    public TableDataInfo<WfMyBusinessVo> list(WfMyBusinessBo bo, PageQuery pageQuery) {
        return iWfMyBusinessService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程业务扩展列表。
     *
     * @param bo        流程业务扩展查询条件
     * @param response  HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:myBusiness:export")
    @Log(title = "流程业务扩展", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfMyBusinessBo bo, HttpServletResponse response) {
        List<WfMyBusinessVo> list = iWfMyBusinessService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程业务扩展", WfMyBusinessVo.class, response);
    }

    /**
     * 获取流程业务扩展详细信息。
     *
     * @param id 主键
     * @return 流程业务扩展详细信息
     */
    @SaCheckPermission("workflow:myBusiness:query")
    @GetMapping("/{id}")
    public R<WfMyBusinessVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfMyBusinessService.queryById(id));
    }

    /**
     * 新增流程业务扩展。
     *
     * @param bo 流程业务扩展新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:myBusiness:add")
    @Log(title = "流程业务扩展", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfMyBusinessBo bo) {
        return toAjax(iWfMyBusinessService.insertByBo(bo));
    }

    /**
     * 修改流程业务扩展。
     *
     * @param bo 流程业务扩展修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:myBusiness:edit")
    @Log(title = "流程业务扩展", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfMyBusinessBo bo) {
        return toAjax(iWfMyBusinessService.updateByBo(bo));
    }

    /**
     * 删除流程业务扩展。
     *
     * @param ids 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:myBusiness:remove")
    @Log(title = "流程业务扩展", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfMyBusinessService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
