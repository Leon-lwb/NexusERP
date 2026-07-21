package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.bo.BdCategoryAttributeBo;
import com.nexus.erp.bd.domain.vo.BdCategoryAttributeVo;
import com.nexus.erp.bd.service.IBdCategoryAttributeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料分类属性模板 Controller
 * <p>
 * 管理分类的可扩展属性定义（如钢材分类挂"材质/规格/产地"），物料按所属分类继承并填写属性值。
 * 权限复用 bd:category:query / bd:category:edit，不单独建菜单按钮。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/category-attr")
public class BdCategoryAttributeController extends BaseController {

    private final IBdCategoryAttributeService bdCategoryAttributeService;

    /**
     * 按分类查询属性模板列表
     * <p>权限标识：{@code bd:category:query}</p>
     */
    @SaCheckPermission("bd:category:query")
    @GetMapping("/list")
    public R<List<BdCategoryAttributeVo>> list(@RequestParam(required = false) Long categoryId) {
        return R.ok(bdCategoryAttributeService.selectListByCategory(categoryId));
    }

    /**
     * 根据主键获取属性模板
     * <p>权限标识：{@code bd:category:query}</p>
     */
    @SaCheckPermission("bd:category:query")
    @GetMapping("/{attrId}")
    public R<BdCategoryAttributeVo> getInfo(@PathVariable Long attrId) {
        return R.ok(bdCategoryAttributeService.selectById(attrId));
    }

    /**
     * 新增属性模板
     * <p>权限标识：{@code bd:category:edit}</p>
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料分类属性", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdCategoryAttributeBo bo) {
        return toAjax(bdCategoryAttributeService.insertByBo(bo));
    }

    /**
     * 修改属性模板
     * <p>权限标识：{@code bd:category:edit}</p>
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料分类属性", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdCategoryAttributeBo bo) {
        return toAjax(bdCategoryAttributeService.updateByBo(bo));
    }

    /**
     * 批量删除属性模板
     * <p>权限标识：{@code bd:category:edit}</p>
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料分类属性", businessType = BusinessType.DELETE)
    @DeleteMapping("/{attrIds}")
    public R<Void> remove(@PathVariable Long[] attrIds) {
        return toAjax(bdCategoryAttributeService.deleteByIds(attrIds));
    }
}
