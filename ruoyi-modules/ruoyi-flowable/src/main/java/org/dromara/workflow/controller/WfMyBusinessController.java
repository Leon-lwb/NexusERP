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
import org.dromara.workflow.domain.vo.WfMyBusinessVo;
import org.dromara.workflow.domain.bo.WfMyBusinessBo;
import org.dromara.workflow.service.IWfMyBusinessService;

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

    private final IWfMyBusinessService iWfMyBusinessService;

    /**
     * 查询流程业务扩展列表
     */
    @SaCheckPermission("workflow:myBusiness:list")
    @GetMapping("/list")
    public TableDataInfo<WfMyBusinessVo> list(WfMyBusinessBo bo, PageQuery pageQuery) {
        return iWfMyBusinessService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程业务扩展列表
     */
    @SaCheckPermission("workflow:myBusiness:export")
    @Log(title = "流程业务扩展", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfMyBusinessBo bo, HttpServletResponse response) {
        List<WfMyBusinessVo> list = iWfMyBusinessService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程业务扩展", WfMyBusinessVo.class, response);
    }

    /**
     * 获取流程业务扩展详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("workflow:myBusiness:query")
    @GetMapping("/{id}")
    public R<WfMyBusinessVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfMyBusinessService.queryById(id));
    }

    /**
     * 新增流程业务扩展
     */
    @SaCheckPermission("workflow:myBusiness:add")
    @Log(title = "流程业务扩展", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfMyBusinessBo bo) {
        return toAjax(iWfMyBusinessService.insertByBo(bo));
    }

    /**
     * 修改流程业务扩展
     */
    @SaCheckPermission("workflow:myBusiness:edit")
    @Log(title = "流程业务扩展", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfMyBusinessBo bo) {
        return toAjax(iWfMyBusinessService.updateByBo(bo));
    }

    /**
     * 删除流程业务扩展
     *
     * @param ids 主键串
     */
    @SaCheckPermission("workflow:myBusiness:remove")
    @Log(title = "流程业务扩展", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfMyBusinessService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
