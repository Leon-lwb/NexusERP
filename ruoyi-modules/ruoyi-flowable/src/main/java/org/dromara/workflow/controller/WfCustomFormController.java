package org.dromara.workflow.controller;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;

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
import org.dromara.workflow.domain.bo.WfCustomFormBo;
import org.dromara.workflow.domain.vo.CustomFormVo;
import org.dromara.workflow.domain.vo.WfCustomFormVo;
import org.dromara.workflow.service.IWfCustomFormService;

import org.springframework.validation.annotation.Validated;

/**
 * 流程业务单
 *
 * @author nbacheng
 * @date 2023-10-09
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/customForm")
public class WfCustomFormController extends BaseController {

    private final IWfCustomFormService iWfCustomFormService;

    /**
     * 查询流程业务单列表
     */
    @SaCheckPermission("workflow:customForm:list")
    @GetMapping("/list")
    public TableDataInfo<WfCustomFormVo> list(WfCustomFormBo bo, PageQuery pageQuery) {
        return iWfCustomFormService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程业务单列表
     */
    @SaCheckPermission("workflow:customForm:export")
    @Log(title = "流程业务单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfCustomFormBo bo, HttpServletResponse response) {
        List<WfCustomFormVo> list = iWfCustomFormService.queryList(bo);
        ExcelUtil.exportExcel(list, "流程业务单", WfCustomFormVo.class, response);
    }

    /**
     * 获取流程业务单详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("workflow:customForm:query")
    @GetMapping("/{id}")
    public R<WfCustomFormVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfCustomFormService.queryById(id));
    }

    /**
     * 新增流程业务单
     */
    @SaCheckPermission("workflow:customForm:add")
    @Log(title = "流程业务单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfCustomFormBo bo) {
        return toAjax(iWfCustomFormService.insertByBo(bo));
    }

    /**
     * 修改流程业务单
     */
    @SaCheckPermission("workflow:customForm:edit")
    @Log(title = "流程业务单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfCustomFormBo bo) {
        return toAjax(iWfCustomFormService.updateByBo(bo));
    }
    
    /**
     * 关联流程业务单
     */
    @SaCheckPermission("workflow:customForm:edit")
    @Log(title = "流程业务单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/updateCustom")
    public R<Void> updateCustom( @RequestBody CustomFormVo customFormVo) {
        iWfCustomFormService.updateCustom(customFormVo);
        return R.ok("关联流程成功!");
    }

    /**
     * 删除流程业务单
     *
     * @param ids 主键串
     */
    @SaCheckPermission("workflow:customForm:remove")
    @Log(title = "流程业务单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(iWfCustomFormService.deleteWithValidByIds(Arrays.asList(ids), true));
    }
}
