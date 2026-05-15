package org.dromara.workflow.controller;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.workflow.domain.bo.WfDdFlowBo;
import org.dromara.workflow.domain.vo.WfDdFlowVo;
import org.dromara.workflow.service.IWfDdFlowService;

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

    private final IWfDdFlowService iWfDdFlowService;

    /**
     * 查询钉钉流程列表
     */
    @SaCheckPermission("workflow:ddFlow:list")
    @GetMapping("/list")
    public TableDataInfo<WfDdFlowVo> list(WfDdFlowBo bo, PageQuery pageQuery) {
        return iWfDdFlowService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出钉钉流程列表
     */
    @SaCheckPermission("workflow:ddFlow:export")
    @Log(title = "钉钉流程", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDdFlowBo bo, HttpServletResponse response) {
        List<WfDdFlowVo> list = iWfDdFlowService.queryList(bo);
        ExcelUtil.exportExcel(list, "钉钉流程", WfDdFlowVo.class, response);
    }

    /**
     * 获取钉钉流程详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("workflow:ddFlow:query")
    @GetMapping("/{id}")
    public R<WfDdFlowVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfDdFlowService.queryById(id));
    }

    /**
     * 新增钉钉流程
     */
    @SaCheckPermission("workflow:ddFlow:add")
    @Log(title = "钉钉流程", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDdFlowBo bo) {
        return toAjax(iWfDdFlowService.insertByBo(bo));
    }

    /**
     * 修改钉钉流程
     */
    @SaCheckPermission("workflow:ddFlow:edit")
    @Log(title = "钉钉流程", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDdFlowBo bo) {
        return toAjax(iWfDdFlowService.updateByBo(bo));
    }

    /**
     * 删除钉钉流程
     *
     * @param ids 主键串
     */
    @SaCheckPermission("workflow:ddFlow:remove")
    @Log(title = "钉钉流程", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfDdFlowService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
