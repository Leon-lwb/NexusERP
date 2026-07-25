package com.nexus.mes.lt.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.lt.domain.bo.LtLotBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.service.ILtLotService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * MES批次 Controller
 * <p>属于 MES 批次管理模块，提供 批次主档 相关 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/lt/lot")
public class LtLotController extends BaseController {

    private final ILtLotService ltLotService;

    /**
     * 分页查询批次列表
     */
    @SaCheckPermission("mes:lt:lot:list")
    @GetMapping("/list")
    public TableDataInfo<LtLotVo> list(LtLotBo bo, PageQuery pageQuery) {
        return ltLotService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取批次详情
     */
    @SaCheckPermission("mes:lt:lot:query")
    @GetMapping("/{lotId}")
    public R<LtLotVo> getInfo(@PathVariable Long lotId) {
        return R.ok(ltLotService.selectById(lotId));
    }

    /**
     * 根据批次编码查询
     */
    @SaCheckPermission("mes:lt:lot:query")
    @GetMapping("/by-code/{lotCode}")
    public R<LtLotVo> getByCode(@PathVariable String lotCode) {
        return R.ok(ltLotService.selectByCode(lotCode));
    }

    /**
     * 新增批次
     */
    @SaCheckPermission("mes:lt:lot:add")
    @Log(title = "批次新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody LtLotBo bo) {
        return toAjax(ltLotService.insertByBo(bo));
    }

    /**
     * 修改批次
     */
    @SaCheckPermission("mes:lt:lot:edit")
    @Log(title = "批次修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody LtLotBo bo) {
        return toAjax(ltLotService.updateByBo(bo));
    }

    /**
     * 批量删除批次
     */
    @SaCheckPermission("mes:lt:lot:remove")
    @Log(title = "批次删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(ltLotService.deleteByIds(ids));
    }

    /**
     * 批次隔离(IN_STOCK/IN_USE -> QUARANTINED)
     */
    @SaCheckPermission("mes:lt:lot:quarantine")
    @Log(title = "批次隔离", businessType = BusinessType.UPDATE)
    @PutMapping("/quarantine/{lotId}")
    public R<Void> quarantine(@PathVariable Long lotId,
                              @RequestParam(required = false) String remark) {
        return toAjax(ltLotService.quarantine(lotId, remark));
    }

    /**
     * 批次报废(QUARANTINED -> SCRAPPED)
     */
    @SaCheckPermission("mes:lt:lot:scrap")
    @Log(title = "批次报废", businessType = BusinessType.UPDATE)
    @PutMapping("/scrap/{lotId}")
    public R<Void> scrap(@PathVariable Long lotId,
                         @RequestParam(required = false) String remark) {
        return toAjax(ltLotService.scrap(lotId, remark));
    }

    /**
     * 批次放行(QUARANTINED -> IN_STOCK,隔离解除)
     */
    @SaCheckPermission("mes:lt:lot:release")
    @Log(title = "批次放行", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{lotId}")
    public R<Void> release(@PathVariable Long lotId) {
        return toAjax(ltLotService.release(lotId));
    }

    /**
     * 更新批次QC状态(PENDING -> PASS/FAIL)
     */
    @SaCheckPermission("mes:lt:lot:qc")
    @Log(title = "批次QC状态更新", businessType = BusinessType.UPDATE)
    @PutMapping("/qc-status/{lotId}")
    public R<Void> updateQcStatus(@PathVariable Long lotId,
                                  @RequestParam String qcStatus) {
        return toAjax(ltLotService.updateQcStatus(lotId, qcStatus));
    }

    /**
     * FEFO 推荐: 按到期升序返回可用批次
     */
    @SaCheckPermission("mes:lt:lot:fefo")
    @GetMapping("/fefo/{materialId}")
    public R<List<LtLotVo>> recommendFefo(@PathVariable Long materialId,
                                          @RequestParam(required = false) BigDecimal requiredQty) {
        return R.ok(ltLotService.recommendFefo(materialId, requiredQty));
    }

    /**
     * 批次追溯链(批次 -> 工单消耗 -> SN 绑定)
     */
    @SaCheckPermission("mes:lt:lot:trace")
    @GetMapping("/trace/{lotId}")
    public R<Map<String, Object>> trace(@PathVariable Long lotId) {
        return R.ok(ltLotService.trace(lotId));
    }
}
