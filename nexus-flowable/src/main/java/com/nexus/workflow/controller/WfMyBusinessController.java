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
import com.nexus.workflow.domain.bo.WfMyBusinessBo;
import com.nexus.workflow.domain.vo.WfMyBusinessVo;
import com.nexus.workflow.service.IWfMyBusinessService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 流程业务扩展
 *
 * @author nbacheng
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
    @PreAuthorize("@ss.hasPermi('workflow:myBusiness:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfMyBusinessVo> list(WfMyBusinessBo bo, PageQuery pageQuery) {
        return iWfMyBusinessService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出我的业务列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:myBusiness:export')")
    @Log(title = "我的业务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfMyBusinessBo bo, HttpServletResponse response) {
        List<WfMyBusinessVo> list = iWfMyBusinessService.queryList(bo);
        ExcelUtil<WfMyBusinessVo> util = new ExcelUtil<>(WfMyBusinessVo.class);
        util.exportExcel(response, list, "我的业务", "我的业务");
    }

    /**
     * 获取流程业务扩展详细信息
     *
     * @param id 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:myBusiness:query')")
    @GetMapping("/{id}")
    public R<WfMyBusinessVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(iWfMyBusinessService.queryById(id));
    }

    /**
     * 新增流程业务扩展
     */
    @PreAuthorize("@ss.hasPermi('workflow:myBusiness:add')")
    @Log(title = "流程业务扩展", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfMyBusinessBo bo) {
        return iWfMyBusinessService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改流程业务扩展
     */
    @PreAuthorize("@ss.hasPermi('workflow:myBusiness:edit')")
    @Log(title = "流程业务扩展", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfMyBusinessBo bo) {
        return iWfMyBusinessService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除流程业务扩展
     *
     * @param ids 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:myBusiness:remove')")
    @Log(title = "流程业务扩展", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return iWfMyBusinessService.deleteWithValidByIds(Arrays.asList(ids), true) ? R.ok() : R.fail();
    }
}
