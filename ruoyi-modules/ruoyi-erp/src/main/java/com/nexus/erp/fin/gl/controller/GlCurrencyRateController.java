package com.nexus.erp.fin.gl.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.gl.domain.bo.GlCurrencyRateBo;
import com.nexus.erp.fin.gl.domain.vo.GlCurrencyRateVo;
import com.nexus.erp.fin.gl.service.IGlCurrencyRateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 币种汇率 Controller
 *
 * @author nexus
 * @date 2026-07-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/gl/currency")
public class GlCurrencyRateController extends BaseController {

    private final IGlCurrencyRateService glCurrencyRateService;

    @SaCheckPermission("fin:gl:currency:list")
    @GetMapping("/list")
    public TableDataInfo<GlCurrencyRateVo> list(GlCurrencyRateBo bo, PageQuery pageQuery) {
        return glCurrencyRateService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission("fin:gl:currency:list")
    @GetMapping("/{rateId}")
    public R<GlCurrencyRateVo> getInfo(@PathVariable Long rateId) {
        return R.ok(glCurrencyRateService.queryById(rateId));
    }

    @SaCheckPermission("fin:gl:currency:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody GlCurrencyRateBo bo) {
        return toAjax(glCurrencyRateService.insertByBo(bo));
    }

    @SaCheckPermission("fin:gl:currency:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody GlCurrencyRateBo bo) {
        return toAjax(glCurrencyRateService.updateByBo(bo));
    }

    @SaCheckPermission("fin:gl:currency:remove")
    @DeleteMapping("/{rateIds}")
    public R<Void> remove(@PathVariable Long[] rateIds) {
        return toAjax(glCurrencyRateService.deleteByIds(rateIds));
    }
}
