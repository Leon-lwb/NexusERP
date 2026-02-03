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
import com.nexus.workflow.domain.bo.WfDdFlowBo;
import com.nexus.workflow.domain.vo.WfDdFlowVo;
import com.nexus.workflow.service.IWfDdFlowService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 钉钉流程
 *
 * @author nbacheng
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
    @PreAuthorize("@ss.hasPermi('workflow:ddFlow:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfDdFlowVo> list(WfDdFlowBo bo, PageQuery pageQuery) {
        return iWfDdFlowService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出钉钉流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:ddFlow:export')")
    @Log(title = "钉钉流程", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDdFlowBo bo, HttpServletResponse response) {
        List<WfDdFlowVo> list = iWfDdFlowService.queryList(bo);
        ExcelUtil<WfDdFlowVo> util = new ExcelUtil<WfDdFlowVo>(WfDdFlowVo.class);
        util.exportExcel(response, list, "钉钉流程", "钉钉流程");
    }

    /**
     * 获取钉钉流程详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:ddFlow:query')")
    @GetMapping("/{id}")
    public R<WfDdFlowVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfDdFlowService.queryById(id));
    }

    /**
     * 新增钉钉流程
     */
    @PreAuthorize("@ss.hasPermi('workflow:ddFlow:add')")
    @Log(title = "钉钉流程", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDdFlowBo bo) {
        return iWfDdFlowService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改钉钉流程
     */
    @PreAuthorize("@ss.hasPermi('workflow:ddFlow:edit')")
    @Log(title = "钉钉流程", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDdFlowBo bo) {
        return iWfDdFlowService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除钉钉流程
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:ddFlow:remove')")
    @Log(title = "钉钉流程", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfDdFlowService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
