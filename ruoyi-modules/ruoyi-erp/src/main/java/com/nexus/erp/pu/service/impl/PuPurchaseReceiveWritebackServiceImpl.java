package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.enums.PurchaseOrderLineReceiveStatus;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.IPuPurchaseReceiveWritebackService;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购收货回写Service实现类，负责将收货数量回写到采购订单行并更新采购单状态
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuPurchaseReceiveWritebackServiceImpl implements IPuPurchaseReceiveWritebackService {

    /** 采购订单Mapper */
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    /** 采购订单行Mapper */
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    /** 物料收货行Mapper */
    private final WmMaterialReceiveLineMapper receiveLineMapper;

    /**
     * 回写采购订单行已收货数量，按物料匹配收货行与采购行，累加已收数量并更新行级收货状态
     *
     * @param receiveId 收货单ID
     * @param purId     采购订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeBackReceivedQty(Long receiveId, Long purId) {
        log.info("开始回写采购订单已收货数量: receiveId={}, purId={}", receiveId, purId);
        if (purId == null) {
            log.warn("采购订单ID为空，跳过回写: receiveId={}", receiveId);
            return;
        }
        List<WmMaterialReceiveLine> receiveLines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>().eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        log.info("查询到收货行数量: {}", receiveLines != null ? receiveLines.size() : 0);
        if (receiveLines == null || receiveLines.isEmpty()) {
            log.warn("收货行为空，跳过回写: receiveId={}", receiveId);
            return;
        }

        List<PuPurchaseOrderLine> orderLines = purchaseOrderLineMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseOrderLine>().eq(PuPurchaseOrderLine::getOrderId, purId));
        log.info("查询到采购订单行数量: {}", orderLines != null ? orderLines.size() : 0);
        if (orderLines == null || orderLines.isEmpty()) {
            log.warn("采购订单行为空，跳过回写: purId={}", purId);
            return;
        }

        for (WmMaterialReceiveLine receiveLine : receiveLines) {
            // 优先按 purchaseLineId 直接匹配
            LambdaQueryWrapper<PuPurchaseOrderLine> lineWrapper;
            if (receiveLine.getPurchaseLineId() != null) {
                lineWrapper = Wrappers.<PuPurchaseOrderLine>lambdaQuery()
                    .eq(PuPurchaseOrderLine::getLineId, receiveLine.getPurchaseLineId());
            } else {
                // 兜底: 按 orderId + materialId 匹配(兼容旧数据)
                lineWrapper = Wrappers.<PuPurchaseOrderLine>lambdaQuery()
                    .eq(PuPurchaseOrderLine::getOrderId, purId)
                    .eq(PuPurchaseOrderLine::getMaterialId, receiveLine.getMaterialId());
            }
            PuPurchaseOrderLine poLine = purchaseOrderLineMapper.selectOne(lineWrapper);
            if (poLine == null) {
                log.warn("收货行 {} 未匹配到采购订单行, 跳过", receiveLine.getLineId());
                continue;
            }
            BigDecimal lineReceivedQty = receiveLine.getQuantityReceived() != null ? receiveLine.getQuantityReceived() : BigDecimal.ZERO;
            BigDecimal currentReceivedQty = poLine.getReceivedQuantity() != null ? poLine.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal newReceivedQty = currentReceivedQty.add(lineReceivedQty);
            PurchaseOrderLineReceiveStatus lineStatus = calcLineReceiveStatus(poLine.getOrderQuantity(), newReceivedQty);
            // 原子累加已收数量(避免并发覆盖), 状态基于预期值计算
            LambdaUpdateWrapper<PuPurchaseOrderLine> updateWrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
                .eq(PuPurchaseOrderLine::getLineId, poLine.getLineId())
                .setSql("received_quantity = received_quantity + " + lineReceivedQty.toPlainString())
                .set(PuPurchaseOrderLine::getReceiveStatus, lineStatus.getCode());
            int rows = purchaseOrderLineMapper.update(null, updateWrapper);
            log.info("更新采购订单行: lineId={}, receiveStatus={}, 影响行数={}", poLine.getLineId(), lineStatus.getCode(), rows);
        }

        // 重新查询订单行以反映最新已收数量,用于订单头状态汇总
        List<PuPurchaseOrderLine> latestOrderLines = purchaseOrderLineMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseOrderLine>().eq(PuPurchaseOrderLine::getOrderId, purId));
        updatePurchaseOrderStatus(purId, latestOrderLines);
    }

    /**
     * 根据订购数量与已收数量计算行级收货状态
     *
     * @param orderQty    订购数量
     * @param receivedQty 已收数量
     * @return 行级收货状态
     */
    private PurchaseOrderLineReceiveStatus calcLineReceiveStatus(BigDecimal orderQty, BigDecimal receivedQty) {
        BigDecimal safeOrderQty = orderQty != null ? orderQty : BigDecimal.ZERO;
        BigDecimal safeReceivedQty = receivedQty != null ? receivedQty : BigDecimal.ZERO;
        int cmp = safeReceivedQty.compareTo(safeOrderQty);
        if (cmp == 0) {
            return PurchaseOrderLineReceiveStatus.FULL_RECEIVED;
        }
        if (cmp > 0) {
            return PurchaseOrderLineReceiveStatus.OVER_RECEIVED;
        }
        if (safeReceivedQty.compareTo(BigDecimal.ZERO) > 0) {
            return PurchaseOrderLineReceiveStatus.PARTIAL_RECEIVED;
        }
        return PurchaseOrderLineReceiveStatus.PENDING;
    }

    /**
     * 根据所有行的收货状态汇总更新采购订单整体状态
     *
     * @param orderId    采购订单ID
     * @param orderLines 采购订单行列表
     */
    private void updatePurchaseOrderStatus(Long orderId, List<PuPurchaseOrderLine> orderLines) {
        boolean allReceived = true;
        boolean anyReceived = false;
        for (PuPurchaseOrderLine line : orderLines) {
            BigDecimal orderQty = line.getOrderQuantity() != null ? line.getOrderQuantity() : BigDecimal.ZERO;
            BigDecimal receivedQty = line.getReceivedQuantity() != null ? line.getReceivedQuantity() : BigDecimal.ZERO;
            if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
                anyReceived = true;
            }
            if (receivedQty.compareTo(orderQty) < 0) {
                allReceived = false;
            }
        }

        PuPurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if (allReceived) {
            order.setStatus("RECEIVED");
        } else if (anyReceived) {
            order.setStatus("PARTIALLY_RECEIVED");
        }
        purchaseOrderMapper.updateById(order);
    }
}
