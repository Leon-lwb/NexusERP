package com.nexus.erp.fin.gl.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.gl.domain.bo.GlPeriodBo;
import com.nexus.erp.fin.gl.domain.vo.GlPeriodVo;
import com.nexus.erp.fin.gl.service.IGlPeriodService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 会计期间 Controller
 *
 * @author nexus
 * @date 2026-07-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/gl/period")
public class GlPeriodController extends BaseController {

    private final IGlPeriodService glPeriodService;

    @SaCheckPermission("fin:gl:period:list")
    @GetMapping("/list")
    public TableDataInfo<GlPeriodVo> list(GlPeriodBo bo, PageQuery pageQuery) {
        return glPeriodService.queryPageList(bo, pageQuery);
    }

    @SaCheckPermission("fin:gl:period:list")
    @GetMapping("/{periodId}")
    public R<GlPeriodVo> getInfo(@PathVariable Long periodId) {
        return R.ok(glPeriodService.queryById(periodId));
    }

    @SaCheckPermission("fin:gl:period:edit")
    @PutMapping("/close/{periodId}")
    public R<Void> close(@PathVariable Long periodId) {
        return toAjax(glPeriodService.closePeriod(periodId));
    }

    @SaCheckPermission("fin:gl:period:edit")
    @PutMapping("/lock/{periodId}")
    public R<Void> lock(@PathVariable Long periodId) {
        return toAjax(glPeriodService.lockPeriod(periodId));
    }

    @SaCheckPermission("fin:gl:period:edit")
    @PutMapping("/reopen/{periodId}")
    public R<Void> reopen(@PathVariable Long periodId) {
        return toAjax(glPeriodService.reopenPeriod(periodId));
    }

    @SaCheckPermission("fin:gl:period:edit")
    @PostMapping("/generateYear/{fiscalYear}")
    public R<Void> generateYear(@PathVariable Integer fiscalYear) {
        return toAjax(glPeriodService.generateYearPeriods(fiscalYear));
    }
}
