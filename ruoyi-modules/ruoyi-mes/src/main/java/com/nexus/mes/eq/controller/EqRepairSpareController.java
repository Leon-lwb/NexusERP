package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.domain.vo.EqRepairSpareVo;
import com.nexus.mes.eq.service.IEqRepairSpareService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MES维修领用备件明细 Controller
 *
 * <p>属于MES 设备管理模块，提供维修备件相关的 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/repair-spare")
public class EqRepairSpareController extends BaseController {

    private final IEqRepairSpareService eqRepairSpareService;

    /**
     * 查询某维修单的备件领用明细
     *
     * @param repairId 维修记录ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:query")
    @GetMapping("/repair/{repairId}")
    public R<List<EqRepairSpareVo>> listByRepair(@PathVariable Long repairId) {
        return R.ok(eqRepairSpareService.selectByRepair(repairId));
    }

    /**
     * 批量新增维修领用明细(扣减备件库存)
     *
     * @param bos 参数 bos
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:complete")
    @Log(title = "维修领用备件", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public R<List<EqRepairSpareVo>> batchAdd(@RequestBody List<EqRepairSpareBo> bos) {
        return R.ok(eqRepairSpareService.batchAdd(bos));
    }

    /**
     * 删除维修领用明细
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:complete")
    @Log(title = "维修领用备件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqRepairSpareService.deleteByIds(ids));
    }
}
