package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.bo.BdBomAlternativeBo;
import com.nexus.erp.bd.domain.vo.BdBomAlternativeVo;
import com.nexus.erp.bd.service.IBdBomAlternativeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BOM替代料Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/bom/alternative")
public class BdBomAlternativeController extends BaseController {

    private final IBdBomAlternativeService bdBomAlternativeService;

    /**
     * 查询某BOM明细行的替代料列表
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/list/{detailId}")
    public R<List<BdBomAlternativeVo>> listByDetailId(@PathVariable Long detailId) {
        return R.ok(bdBomAlternativeService.listByDetailId(detailId));
    }

    /**
     * 查询某BOM的所有替代料
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/bom/{bomId}")
    public R<List<BdBomAlternativeVo>> listByBomId(@PathVariable Long bomId) {
        return R.ok(bdBomAlternativeService.listByBomId(bomId));
    }

    /**
     * 批量保存替代料
     */
    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM替代料", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public R<Void> saveBatch(@RequestParam Long detailId, @RequestParam Long bomId,
                              @RequestBody List<BdBomAlternativeBo> list) {
        return toAjax(bdBomAlternativeService.saveBatch(detailId, bomId, list));
    }

    /**
     * 删除替代料
     */
    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM替代料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdBomAlternativeService.deleteByIds(ids));
    }

    /**
     * 获取可用替代料（考虑生效/失效日期）
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/available/{detailId}")
    public R<List<BdBomAlternativeVo>> getAvailableAlternatives(@PathVariable Long detailId) {
        return R.ok(bdBomAlternativeService.getAvailableAlternatives(detailId));
    }
}
