package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockTxBo;
import com.nexus.erp.wm.domain.bo.WmStockReservationBo;
import com.nexus.erp.wm.domain.bo.WmStockUnreserveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockTxVo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.service.IWmMaterialStockService;
import com.nexus.erp.wm.service.IWmMaterialStockTxService;
import com.nexus.erp.wm.service.IWmStockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 库存预留 Controller
 * <p>
 * 提供库存预留的查询与手动操作接口：预留明细（RESERVE/UNRESERVE 流水）、
 * 预留汇总、手动预留、释放预留与可用库存检查。
 * <p>
 * 自动预留由销售订单确认/工单下达触发（{@link IWmStockReservationService}），
 * 本控制器侧重预留的查询与异常场景的手动调整。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/reservation")
public class WmStockReservationController extends BaseController {

    /** 库存预留服务，提供预留/释放/可用量检查能力 */
    private final IWmStockReservationService wmStockReservationService;
    /** 库存流水服务，提供预留明细（RESERVE/UNRESERVE 事务）查询能力 */
    private final IWmMaterialStockTxService wmMaterialStockTxService;
    /** 库存清单服务，提供预留汇总（在库/已预留/可用）查询能力 */
    private final IWmMaterialStockService wmMaterialStockService;

    /**
     * 分页查询预留明细列表
     * <p>
     * 查询 RESERVE / UNRESERVE 类型库存事务，可按物料、单据类型、单据编号筛选。
     * <p>
     * 权限标识：{@code wm:reservation:list}
     *
     * @param bo        查询参数，复用库存事务 BO（txType 为空时返回 RESERVE/UNRESERVE 两类）
     * @param pageQuery 分页参数
     * @return 预留明细分页列表
     */
    @SaCheckPermission("wm:reservation:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockTxVo> list(WmMaterialStockTxBo bo, PageQuery pageQuery) {
        if (StringUtils.isBlank(bo.getTxType())) {
            bo.setTxType("RESERVE,UNRESERVE");
        }
        return wmMaterialStockTxService.selectPageList(bo, pageQuery);
    }

    /**
     * 分页查询预留汇总列表
     * <p>
     * 按物料 + 仓库聚合库存，展示在库数量 / 已预留数量 / 可用数量。
     * <p>
     * 权限标识：{@code wm:reservation:summary}
     *
     * @param bo        查询参数（物料编码/名称、仓库等）
     * @param pageQuery 分页参数
     * @return 预留汇总分页列表
     */
    @SaCheckPermission("wm:reservation:summary")
    @GetMapping("/summary")
    public TableDataInfo<WmMaterialStockVo> summary(WmMaterialStockBo bo, PageQuery pageQuery) {
        return wmMaterialStockService.selectPageList(bo, pageQuery);
    }

    /**
     * 手动预留库存
     * <p>
     * 按物料 + 仓库 FIFO 多批次锁定库存，返回实际预留数量（不足时返回部分）。
     * <p>
     * 权限标识：{@code wm:reservation:add}
     * <p>日志记录：title="库存预留"，businessType=INSERT
     *
     * @param bo 预留参数（materialId/warehouseId/reserveQty/sourceDocType/sourceDocId/sourceDocLineId）
     * @return 实际预留数量
     */
    @SaCheckPermission("wm:reservation:add")
    @Log(title = "库存预留", businessType = BusinessType.INSERT)
    @PostMapping("/reserve")
    public R<BigDecimal> reserve(@Validated @RequestBody WmStockReservationBo bo) {
        return R.ok(wmStockReservationService.reserve(
            bo.getMaterialId(), bo.getWarehouseId(), bo.getReserveQty(),
            bo.getSourceDocType(), bo.getSourceDocId(), bo.getSourceDocLineId()));
    }

    /**
     * 释放预留
     * <p>
     * 按来源单据（类型 + ID + 行ID）释放全部对应预留。
     * <p>
     * 权限标识：{@code wm:reservation:edit}
     * <p>日志记录：title="库存预留"，businessType=UPDATE
     *
     * @param bo 释放参数（sourceDocType/sourceDocId/sourceDocLineId）
     * @return 释放数量
     */
    @SaCheckPermission("wm:reservation:edit")
    @Log(title = "库存预留", businessType = BusinessType.UPDATE)
    @PostMapping("/unreserve")
    public R<BigDecimal> unreserve(@Validated @RequestBody WmStockUnreserveBo bo) {
        return R.ok(wmStockReservationService.unreserve(
            bo.getSourceDocType(), bo.getSourceDocId(), bo.getSourceDocLineId()));
    }

    /**
     * 检查可用库存
     * <p>
     * 返回某物料（可按仓库过滤）的可用数量 = 在库数量 - 已预留数量。
     * <p>
     * 权限标识：{@code wm:reservation:query}
     *
     * @param materialId  物料 ID
     * @param warehouseId 仓库 ID（可为空，表示不限仓库）
     * @return 可用数量
     */
    @SaCheckPermission("wm:reservation:query")
    @GetMapping("/available/{materialId}")
    public R<BigDecimal> available(@PathVariable Long materialId, Long warehouseId) {
        return R.ok(wmStockReservationService.checkAvailable(materialId, warehouseId));
    }
}
