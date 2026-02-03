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
import com.nexus.workflow.domain.bo.WfDefaultOperateBo;
import com.nexus.workflow.domain.vo.WfDefaultOperateVo;
import com.nexus.workflow.service.IWfDefaultOperateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 流程默认操作
 *
 * @author nbacheng
 * @date 2023-11-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/defaultOperate")
public class WfDefaultOperateController extends BaseController {

    private final IWfDefaultOperateService iWfDefaultOperateService;

    /**
     * 查询流程默认操作列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:defaultOperate:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfDefaultOperateVo> list(WfDefaultOperateBo bo, PageQuery pageQuery) {
        return iWfDefaultOperateService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出流程默认操作列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:defaultOperate:export')")
    @Log(title = "流程默认操作", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDefaultOperateBo bo, HttpServletResponse response) {
        List<WfDefaultOperateVo> list = iWfDefaultOperateService.queryList(bo);
        ExcelUtil<WfDefaultOperateVo> util = new ExcelUtil<>(WfDefaultOperateVo.class);
        util.exportExcel(response, list, "流程默认操作", "流程默认操作");
    }

    /**
     * 获取流程默认操作详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:defaultOperate:query')")
    @GetMapping("/{id}")
    public R<WfDefaultOperateVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfDefaultOperateService.queryById(id));
    }

    /**
     * 新增流程默认操作
     */
    @PreAuthorize("@ss.hasPermi('workflow:defaultOperate:add')")
    @Log(title = "流程默认操作", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDefaultOperateBo bo) {
        return iWfDefaultOperateService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改流程默认操作
     */
    @PreAuthorize("@ss.hasPermi('workflow:defaultOperate:edit')")
    @Log(title = "流程默认操作", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDefaultOperateBo bo) {
        return iWfDefaultOperateService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除流程默认操作
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:defaultOperate:remove')")
    @Log(title = "流程默认操作", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfDefaultOperateService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
