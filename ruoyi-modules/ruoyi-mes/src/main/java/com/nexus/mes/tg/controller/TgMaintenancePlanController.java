package com.nexus.mes.tg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.tg.domain.bo.TgMaintenancePlanBo;
import com.nexus.mes.tg.domain.vo.TgMaintenancePlanVo;
import com.nexus.mes.tg.service.ITgMaintenancePlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES工装保养计划 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/tg/maintenance-plan")
public class TgMaintenancePlanController extends BaseController {

    private final ITgMaintenancePlanService tgMaintenancePlanService;

    @SaCheckPermission("mes:tg:plan:list")
    @GetMapping("/list")
    public TableDataInfo<TgMaintenancePlanVo> list(TgMaintenancePlanBo bo, PageQuery pageQuery) {
        return tgMaintenancePlanService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:tg:plan:query")
    @GetMapping("/{planId}")
    public R<TgMaintenancePlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(tgMaintenancePlanService.selectById(planId));
    }

    @SaCheckPermission("mes:tg:plan:add")
    @Log(title = "工装保养计划新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TgMaintenancePlanBo bo) {
        return toAjax(tgMaintenancePlanService.insertByBo(bo));
    }

    @SaCheckPermission("mes:tg:plan:edit")
    @Log(title = "工装保养计划修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TgMaintenancePlanBo bo) {
        return toAjax(tgMaintenancePlanService.updateByBo(bo));
    }

    @SaCheckPermission("mes:tg:plan:remove")
    @Log(title = "工装保养计划删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(tgMaintenancePlanService.deleteByIds(ids));
    }
}
