package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // 按物料ID建立采购订单行映射
        Map<Long, List<PuPurchaseOrderLine>> materialLineMap = new LinkedHashMap<>();
        // 按物料编码建立兜底映射（当materialId不一致时，通过materialCode匹配）
        Map<String, List<PuPurchaseOrderLine>> materialCodeLineMap = new LinkedHashMap<>();
        for (PuPurchaseOrderLine orderLine : orderLines) {
            materialLineMap.computeIfAbsent(orderLine.getMaterialId(), k -> new java.util.ArrayList<>()).add(orderLine);
            if (orderLine.getMaterialCode() != null) {
                materialCodeLineMap.computeIfAbsent(orderLine.getMaterialCode(), k -> new java.util.ArrayList<>()).add(orderLine);
            }
        }

        for (WmMaterialReceiveLine receiveLine : receiveLines) {
            // 优先按物料ID匹配
            List<PuPurchaseOrderLine> candidates = materialLineMap.get(receiveLine.getMaterialId());
            // 物料ID未匹配时，按物料编码兜底匹配
            if ((candidates == null || candidates.isEmpty()) && receiveLine.getMaterialCode() != null) {
                candidates = materialCodeLineMap.get(receiveLine.getMaterialCode());
                if (candidates != null && !candidates.isEmpty()) {
                    log.info("物料ID未匹配，通过物料编码兜底匹配: materialId={}, materialCode={}",
                        receiveLine.getMaterialId(), receiveLine.getMaterialCode());
                }
            }
            PuPurchaseOrderLine orderLine = null;
            if (candidates != null && !candidates.isEmpty()) {
                orderLine = candidates.remove(0);
            }
            if (orderLine != null) {
                BigDecimal receivedQty = orderLine.getReceivedQuantity() != null ? orderLine.getReceivedQuantity() : BigDecimal.ZERO;
                BigDecimal lineReceivedQty = receiveLine.getQuantityReceived() != null ? receiveLine.getQuantityReceived() : BigDecimal.ZERO;
                BigDecimal newReceivedQty = receivedQty.add(lineReceivedQty);
                orderLine.setReceivedQuantity(newReceivedQty);
                log.info("匹配成功: materialId={}, 收货行数量={}, 原已收数量={}, 新已收数量={}",
                    receiveLine.getMaterialId(), lineReceivedQty, receivedQty, newReceivedQty);

                BigDecimal orderQty = orderLine.getOrderQuantity() != null ? orderLine.getOrderQuantity() : BigDecimal.ZERO;
                int cmp = newReceivedQty.compareTo(orderQty);
                if (cmp == 0) {
                    orderLine.setReceiveStatus("FULL_RECEIVED");
                } else if (cmp > 0) {
                    orderLine.setReceiveStatus("OVER_RECEIVED");
                } else {
                    orderLine.setReceiveStatus("PARTIAL_RECEIVED");
                }

                int rows = purchaseOrderLineMapper.updateById(orderLine);
                log.info("更新采购订单行: lineId={}, receiveStatus={}, 影响行数={}", orderLine.getLineId(), orderLine.getReceiveStatus(), rows);
            } else {
                log.warn("未匹配到采购订单行: materialId={}, materialCode={}",
                    receiveLine.getMaterialId(), receiveLine.getMaterialCode());
            }
        }

        updatePurchaseOrderStatus(purId, orderLines);
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
