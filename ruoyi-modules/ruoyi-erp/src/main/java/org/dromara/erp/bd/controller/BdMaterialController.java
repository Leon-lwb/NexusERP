package org.dromara.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.bd.domain.bo.BdMaterialBo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;
import org.dromara.erp.bd.service.IBdMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/material")
public class BdMaterialController extends BaseController {

    private final IBdMaterialService bdMaterialService;

    @SaCheckPermission("bd:material:list")
    @GetMapping("/list")
    public TableDataInfo<BdMaterialVo> list(BdMaterialBo bo, PageQuery pageQuery) {
        return bdMaterialService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:material:export")
    @Log(title = "物料产品主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdMaterialBo bo, HttpServletResponse response) {
        List<BdMaterialVo> list = bdMaterialService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料产品主数据", BdMaterialVo.class, response);
    }

    @SaCheckPermission("bd:material:query")
    @GetMapping("/{materialId}")
    public R<BdMaterialVo> getInfo(@PathVariable Long materialId) {
        return R.ok(bdMaterialService.selectById(materialId));
    }

    @SaCheckPermission("bd:material:add")
    @Log(title = "物料产品主", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdMaterialBo bo) {
        return toAjax(bdMaterialService.insertByBo(bo));
    }

    @SaCheckPermission("bd:material:edit")
    @Log(title = "物料产品主", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdMaterialBo bo) {
        return toAjax(bdMaterialService.updateByBo(bo));
    }

    @SaCheckPermission("bd:material:remove")
    @Log(title = "物料产品主", businessType = BusinessType.DELETE)
    @DeleteMapping("/{materialIds}")
    public R<Void> remove(@PathVariable Long[] materialIds) {
        return toAjax(bdMaterialService.deleteByIds(materialIds));
    }
}
