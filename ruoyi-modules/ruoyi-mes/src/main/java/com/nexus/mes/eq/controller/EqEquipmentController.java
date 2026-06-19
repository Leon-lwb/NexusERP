package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.service.IEqEquipmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES设备台账 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/equipment")
public class EqEquipmentController extends BaseController {

    private final IEqEquipmentService eqEquipmentService;

    /**
     * 分页查询设备台账列表
     */
    @SaCheckPermission("mes:eq:equipment:list")
    @GetMapping("/list")
    public TableDataInfo<EqEquipmentVo> list(EqEquipmentBo bo, PageQuery pageQuery) {
        return eqEquipmentService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取设备详情
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}")
    public R<EqEquipmentVo> getInfo(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.selectById(equipmentId));
    }

    /**
     * 新增设备
     */
    @SaCheckPermission("mes:eq:equipment:add")
    @Log(title = "设备新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqEquipmentVo> add(@RequestBody EqEquipmentBo bo) {
        return R.ok(eqEquipmentService.insertByBo(bo));
    }

    /**
     * 编辑设备
     */
    @SaCheckPermission("mes:eq:equipment:edit")
    @Log(title = "设备编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<EqEquipmentVo> edit(@RequestBody EqEquipmentBo bo) {
        return R.ok(eqEquipmentService.updateByBo(bo));
    }

    /**
     * 批量删除设备
     */
    @SaCheckPermission("mes:eq:equipment:remove")
    @Log(title = "设备删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqEquipmentService.deleteByIds(ids));
    }
}
