package com.nexus.mes.lt.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.lt.domain.bo.LtWoConsumptionBo;
import com.nexus.mes.lt.domain.bo.LtWoPickBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.domain.vo.LtWoConsumptionVo;
import com.nexus.mes.lt.service.ILtWoConsumptionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES工单批次消耗 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/lt/consumption")
public class LtWoConsumptionController extends BaseController {

    private final ILtWoConsumptionService consumptionService;

    /**
     * 分页查询工单批次消耗列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 工单批次消耗分页数据
     */
    @SaCheckPermission("mes:lt:consumption:list")
    @GetMapping("/list")
    public TableDataInfo<LtWoConsumptionVo> list(LtWoConsumptionBo bo, PageQuery pageQuery) {
        return consumptionService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取消耗记录详情
     *
     * @param consumptionId 消耗记录ID
     * @return 消耗记录详情
     */
    @SaCheckPermission("mes:lt:consumption:query")
    @GetMapping("/{consumptionId}")
    public R<LtWoConsumptionVo> getInfo(@PathVariable Long consumptionId) {
        return R.ok(consumptionService.selectById(consumptionId));
    }

    /**
     * 新增工单批次消耗(联动扣减批次数量)
     *
     * @param bo 工单批次消耗业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:lt:consumption:add")
    @Log(title = "工单批次消耗新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody LtWoConsumptionBo bo) {
        return toAjax(consumptionService.insertByBo(bo));
    }

    /**
     * 批量删除消耗记录
     *
     * @param ids 消耗记录ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:lt:consumption:remove")
    @Log(title = "工单批次消耗删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(consumptionService.deleteByIds(ids));
    }

    /**
     * 按工单ID查询所有批次消耗
     *
     * @param woId 工单ID
     * @return 消耗记录列表
     */
    @SaCheckPermission("mes:lt:consumption:list")
    @GetMapping("/by-wo/{woId}")
    public R<List<LtWoConsumptionVo>> listByWo(@PathVariable Long woId) {
        return R.ok(consumptionService.selectByWoId(woId));
    }

    /**
     * 按批次ID查询所有消耗记录(追溯用)
     *
     * @param lotId 批次ID
     * @return 消耗记录列表
     */
    @SaCheckPermission("mes:lt:consumption:list")
    @GetMapping("/by-lot/{lotId}")
    public R<List<LtWoConsumptionVo>> listByLot(@PathVariable Long lotId) {
        return R.ok(consumptionService.selectByLotId(lotId));
    }

    /**
     * 工单 FEFO 推荐: 按到期升序返回可用批次(两步领料第一步)
     *
     * @param woId        工单ID
     * @param materialId  物料ID
     * @param requiredQty 需求数量(可空,传值则累加直到满足)
     * @return 推荐批次列表
     */
    @SaCheckPermission("mes:lt:lot:fefo")
    @GetMapping("/fefo/{woId}/{materialId}")
    public R<List<LtLotVo>> recommendForWo(@PathVariable Long woId,
                                           @PathVariable Long materialId,
                                           @RequestParam(required = false) BigDecimal requiredQty) {
        return R.ok(consumptionService.recommendForWo(woId, materialId, requiredQty));
    }

    /**
     * 工单 FEFO 领料确认: 逐条扣减批次数量 + 写消耗记录(+可选 SN↔批次绑定)
     *
     * @param pickBo 领料请求(woId/materialId/snId + picks)
     * @return 操作结果
     */
    @SaCheckPermission("mes:lt:consumption:add")
    @Log(title = "工单FEFO领料", businessType = BusinessType.INSERT)
    @PostMapping("/pick")
    public R<Void> confirmPick(@RequestBody LtWoPickBo pickBo) {
        return toAjax(consumptionService.confirmPick(pickBo));
    }
}
