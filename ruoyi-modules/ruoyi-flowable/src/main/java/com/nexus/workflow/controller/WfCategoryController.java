package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.bo.WfCategoryBo;
import com.nexus.workflow.domain.vo.WfAppTypeVo;
import com.nexus.workflow.domain.vo.WfCategoryExportVo;
import com.nexus.workflow.domain.vo.WfCategoryVo;
import com.nexus.workflow.service.IWfCategoryService;

import java.util.Arrays;
import java.util.List;

/**
 * 流程分类Controller
 * <p>提供流程分类的增删改查、导出及 APP 分类查询等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/category")
public class WfCategoryController extends BaseController {

    /** 流程分类业务服务，提供分类查询、新增、修改、删除及编码唯一性校验等能力。 */
    private final IWfCategoryService categoryService;

    /**
     * 分页查询流程分类列表。
     *
     * @param categoryBo 流程分类查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程分类列表及分页元信息
     */
    @SaCheckPermission("workflow:category:list")
    @GetMapping("/list")
    public TableDataInfo<WfCategoryVo> list(WfCategoryBo categoryBo, PageQuery pageQuery) {
        return categoryService.queryPageList(categoryBo, pageQuery);
    }

    /**
     * 查询全部的流程分类列表。
     *
     * @param categoryBo 流程分类查询条件
     * @return 全部流程分类列表
     */
    @SaCheckLogin
    @GetMapping("/listAll")
    public R<List<WfCategoryVo>> listAll(WfCategoryBo categoryBo) {
        return R.ok(categoryService.queryList(categoryBo));
    }

    /**
     * 导出流程分类列表。
     *
     * @param categoryBo 流程分类查询条件
     * @param response   HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:category:export")
    @Log(title = "流程分类", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfCategoryBo categoryBo, HttpServletResponse response) {
        List<WfCategoryVo> list = categoryService.queryList(categoryBo);
        List<WfCategoryExportVo> listVo = MapstructUtils.convert(list, WfCategoryExportVo.class);
        ExcelUtil.exportExcel(listVo, "流程分类", WfCategoryExportVo.class, response);
    }

    /**
     * 获取流程分类详细信息。
     *
     * @param categoryId 分类主键
     * @return 流程分类详细信息
     */
    @SaCheckPermission("workflow:category:query")
    @GetMapping("/{categoryId}")
    public R<WfCategoryVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("categoryId") Long categoryId) {
        return R.ok(categoryService.queryById(categoryId));
    }

    /**
     * 根据分类编码获取流程 APP 分类详细信息。
     *
     * @param code 分类编码
     * @return 流程 APP 分类列表
     */
    @GetMapping("/appType/{code}")
    public R<List<WfAppTypeVo>> getInfoByCode(@NotNull(message = "主键不能为空") @PathVariable("code") String code) {
        return R.ok(categoryService.queryInfoByCode(code));
    }

    /**
     * 新增流程分类。
     *
     * @param categoryBo 流程分类新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:category:add")
    @Log(title = "流程分类", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfCategoryBo categoryBo) {
        if (!categoryService.checkCategoryCodeUnique(categoryBo)) {
            return R.fail("新增流程分类'" + categoryBo.getCategoryName() + "'失败，流程编码已存在");
        }
        return toAjax(categoryService.insertCategory(categoryBo));
    }

    /**
     * 修改流程分类。
     *
     * @param categoryBo 流程分类修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:category:edit")
    @Log(title = "流程分类", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfCategoryBo categoryBo) {
        if (!categoryService.checkCategoryCodeUnique(categoryBo)) {
            return R.fail("修改流程分类'" + categoryBo.getCategoryName() + "'失败，流程编码已存在");
        }
        return toAjax(categoryService.updateCategory(categoryBo));
    }

    /**
     * 删除流程分类。
     *
     * @param categoryIds 分类主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:category:remove")
    @Log(title = "流程分类" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] categoryIds) {
        return toAjax(categoryService.deleteWithValidByIds(Arrays.asList(categoryIds), true));
    }
}
