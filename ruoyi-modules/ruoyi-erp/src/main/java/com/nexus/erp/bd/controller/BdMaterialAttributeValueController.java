package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.bo.BdMaterialAttributeValueBo;
import com.nexus.erp.bd.domain.vo.BdMaterialAttributeValueVo;
import com.nexus.erp.bd.service.IBdMaterialAttributeValueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料扩展属性值 Controller
 * <p>
 * 物料按所属分类继承属性模板并填写属性值。整体保存为替换语义（含必填校验）。
 * 权限复用 bd:material:query / bd:material:edit。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/material-attr-value")
public class BdMaterialAttributeValueController extends BaseController {

    private final IBdMaterialAttributeValueService bdMaterialAttributeValueService;

    /**
     * 按物料查询属性值列表
     * <p>权限标识：{@code bd:material:query}</p>
     */
    @SaCheckPermission("bd:material:query")
    @GetMapping("/{materialId}")
    public R<List<BdMaterialAttributeValueVo>> list(@PathVariable Long materialId) {
        return R.ok(bdMaterialAttributeValueService.listByMaterial(materialId));
    }

    /**
     * 按物料整体保存属性值（替换语义，含必填校验）
     * <p>权限标识：{@code bd:material:edit}</p>
     *
     * @param materialId 物料ID
     * @param values     属性值列表
     */
    @SaCheckPermission("bd:material:edit")
    @Log(title = "物料扩展属性值", businessType = BusinessType.UPDATE)
    @PostMapping("/{materialId}")
    public R<Void> save(@PathVariable Long materialId, @RequestBody List<BdMaterialAttributeValueBo> values) {
        return toAjax(bdMaterialAttributeValueService.saveByMaterial(materialId, values));
    }

    /**
     * 按物料删除全部属性值
     * <p>权限标识：{@code bd:material:edit}</p>
     */
    @SaCheckPermission("bd:material:edit")
    @Log(title = "物料扩展属性值", businessType = BusinessType.DELETE)
    @DeleteMapping("/{materialId}")
    public R<Void> remove(@PathVariable Long materialId) {
        return toAjax(bdMaterialAttributeValueService.deleteByMaterial(materialId));
    }
}
