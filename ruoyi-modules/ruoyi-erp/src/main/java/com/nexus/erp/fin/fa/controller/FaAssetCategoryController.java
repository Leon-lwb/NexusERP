package com.nexus.erp.fin.fa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.fa.domain.bo.FaAssetCategoryBo;
import com.nexus.erp.fin.fa.domain.vo.FaAssetCategoryVo;
import com.nexus.erp.fin.fa.service.IFaAssetCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 固定资产类别 Controller
 *
 * @author nexus
 * @date 2026-08-01
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/fa/category")
public class FaAssetCategoryController extends BaseController {

    private final IFaAssetCategoryService faAssetCategoryService;

    /** 分页查询资产类别 */
    @SaCheckPermission("fin:fa:category:list")
    @GetMapping("/list")
    public TableDataInfo<FaAssetCategoryVo> list(FaAssetCategoryBo bo, PageQuery pageQuery) {
        return faAssetCategoryService.queryPageList(bo, pageQuery);
    }

    /** 查询全部启用类别(下拉用) */
    @SaCheckPermission("fin:fa:category:list")
    @GetMapping("/all")
    public R<List<FaAssetCategoryVo>> all() {
        return R.ok(faAssetCategoryService.queryAll());
    }

    /** 查询资产类别详情 */
    @SaCheckPermission("fin:fa:category:list")
    @GetMapping("/{categoryId}")
    public R<FaAssetCategoryVo> getInfo(@PathVariable Long categoryId) {
        return R.ok(faAssetCategoryService.queryById(categoryId));
    }

    /** 新增资产类别 */
    @SaCheckPermission("fin:fa:category:add")
    @Log(title = "资产类别新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FaAssetCategoryBo bo) {
        return toAjax(faAssetCategoryService.insertByBo(bo));
    }

    /** 修改资产类别 */
    @SaCheckPermission("fin:fa:category:edit")
    @Log(title = "资产类别修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FaAssetCategoryBo bo) {
        return toAjax(faAssetCategoryService.updateByBo(bo));
    }

    /** 删除资产类别 */
    @SaCheckPermission("fin:fa:category:remove")
    @Log(title = "资产类别删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public R<Void> remove(@PathVariable Long[] categoryIds) {
        return toAjax(faAssetCategoryService.deleteByIds(categoryIds));
    }
}
