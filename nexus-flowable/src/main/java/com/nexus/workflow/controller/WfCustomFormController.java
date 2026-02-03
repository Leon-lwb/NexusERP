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
import com.nexus.workflow.domain.bo.WfCustomFormBo;
import com.nexus.workflow.domain.vo.CustomFormVo;
import com.nexus.workflow.domain.vo.WfCustomFormVo;
import com.nexus.workflow.service.IWfCustomFormService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

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
    @PreAuthorize("@ss.hasPermi('workflow:customForm:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfCustomFormVo> list(WfCustomFormBo bo, PageQuery pageQuery) {
        return iWfCustomFormService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程业务单列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:customForm:export')")
    @Log(title = "流程业务单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfCustomFormBo bo, HttpServletResponse response) {
        List<WfCustomFormVo> list = iWfCustomFormService.queryList(bo);
        ExcelUtil<WfCustomFormVo> util = new ExcelUtil<WfCustomFormVo>(WfCustomFormVo.class);
        util.exportExcel(response, list, "流程业务单", "流程业务单");
    }

    /**
     * 获取流程业务单详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:customForm:query')")
    @GetMapping("/{id}")
    public R<WfCustomFormVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfCustomFormService.queryById(id));
    }

    /**
     * 新增流程业务单
     */
    @PreAuthorize("@ss.hasPermi('workflow:customForm:add')")
    @Log(title = "流程业务单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfCustomFormBo bo) {
        return iWfCustomFormService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改流程业务单
     */
    @PreAuthorize("@ss.hasPermi('workflow:customForm:edit')")
    @Log(title = "流程业务单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfCustomFormBo bo) {
        return iWfCustomFormService.updateByBo(bo) ? R.ok() : R.fail();
    }
    
    /**
     * 关联流程业务单
     */
    @PreAuthorize("@ss.hasPermi('workflow:customForm:edit')")
    @Log(title = "流程业务单", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/updateCustom")
    public R<String> updateCustom( @RequestBody CustomFormVo customFormVo) {
        iWfCustomFormService.updateCustom(customFormVo);
        return R.ok("关联流程成功!");
    }

    /**
     * 删除流程业务单
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:customForm:remove')")
    @Log(title = "流程业务单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfCustomFormService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
