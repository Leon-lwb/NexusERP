package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqMaintenancePlanBo;
import com.nexus.mes.eq.domain.vo.EqMaintenancePlanVo;
import com.nexus.mes.eq.service.IEqMaintenancePlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES设备保养计划 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/maintenance-plan")
public class EqMaintenancePlanController extends BaseController {

    private final IEqMaintenancePlanService eqMaintenancePlanService;

    /**
     * 分页查询保养计划列表
     */
    @SaCheckPermission("mes:eq:maintenance:list")
    @GetMapping("/list")
    public TableDataInfo<EqMaintenancePlanVo> list(EqMaintenancePlanBo bo, PageQuery pageQuery) {
        return eqMaintenancePlanService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取保养计划详情
     */
    @SaCheckPermission("mes:eq:maintenance:query")
    @GetMapping("/{planId}")
    public R<EqMaintenancePlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(eqMaintenancePlanService.selectById(planId));
    }

    /**
     * 新增保养计划(自动计算下次保养日期)
     */
    @SaCheckPermission("mes:eq:maintenance:add")
    @Log(title = "保养计划新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqMaintenancePlanVo> add(@RequestBody EqMaintenancePlanBo bo) {
        return R.ok(eqMaintenancePlanService.insertByBo(bo));
    }

    /**
     * 编辑保养计划(自动计算下次保养日期)
     */
    @SaCheckPermission("mes:eq:maintenance:edit")
    @Log(title = "保养计划编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<EqMaintenancePlanVo> edit(@RequestBody EqMaintenancePlanBo bo) {
        return R.ok(eqMaintenancePlanService.updateByBo(bo));
    }

    /**
     * 批量删除保养计划
     */
    @SaCheckPermission("mes:eq:maintenance:remove")
    @Log(title = "保养计划删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqMaintenancePlanService.deleteByIds(ids));
    }
}
