package com.nexus.erp.fin.fa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.fa.domain.bo.FaAssetBo;
import com.nexus.erp.fin.fa.domain.vo.FaAssetVo;
import com.nexus.erp.fin.fa.service.IFaAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 固定资产卡片 Controller
 *
 * @author nexus
 * @date 2026-08-01
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/fa/asset")
public class FaAssetController extends BaseController {

    private final IFaAssetService faAssetService;

    /** 分页查询资产卡片 */
    @SaCheckPermission("fin:fa:asset:list")
    @GetMapping("/list")
    public TableDataInfo<FaAssetVo> list(FaAssetBo bo, PageQuery pageQuery) {
        return faAssetService.queryPageList(bo, pageQuery);
    }

    /** 查询资产卡片详情 */
    @SaCheckPermission("fin:fa:asset:list")
    @GetMapping("/{assetId}")
    public R<FaAssetVo> getInfo(@PathVariable Long assetId) {
        return R.ok(faAssetService.queryById(assetId));
    }

    /** 新增资产卡片 */
    @SaCheckPermission("fin:fa:asset:add")
    @Log(title = "资产卡片新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FaAssetBo bo) {
        return toAjax(faAssetService.insertByBo(bo));
    }

    /** 修改资产卡片 */
    @SaCheckPermission("fin:fa:asset:edit")
    @Log(title = "资产卡片修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FaAssetBo bo) {
        return toAjax(faAssetService.updateByBo(bo));
    }

    /** 删除资产卡片 */
    @SaCheckPermission("fin:fa:asset:remove")
    @Log(title = "资产卡片删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{assetIds}")
    public R<Void> remove(@PathVariable Long[] assetIds) {
        return toAjax(faAssetService.deleteByIds(assetIds));
    }

    /** 原值调整 */
    @SaCheckPermission("fin:fa:asset:adjust")
    @Log(title = "资产原值调整", businessType = BusinessType.UPDATE)
    @PutMapping("/adjustValue")
    public R<Void> adjustValue(@RequestParam Long assetId, @RequestParam BigDecimal newValue) {
        return toAjax(faAssetService.adjustValue(assetId, newValue));
    }

    /** 转固(CWIP->IN_USE) */
    @SaCheckPermission("fin:fa:asset:activate")
    @Log(title = "资产转固", businessType = BusinessType.UPDATE)
    @PutMapping("/activate/{assetId}")
    public R<Void> activate(@PathVariable Long assetId) {
        return toAjax(faAssetService.activate(assetId));
    }
}
