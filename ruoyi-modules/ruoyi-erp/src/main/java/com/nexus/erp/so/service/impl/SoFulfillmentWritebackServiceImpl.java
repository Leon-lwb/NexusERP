package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 销售订单履行数量回写Service实现
 * <p>
 * 采用 setSql 原子累加，规避读后写并发冲突；soLineId 为空或 qty<=0 时跳过。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoFulfillmentWritebackServiceImpl implements ISoFulfillmentWritebackService {

    private final SoSalesOrderLineMapper soSalesOrderLineMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addShippedQty(Long soLineId, BigDecimal qty) {
        if (soLineId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String qtyStr = qty.toPlainString();
        LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, soLineId)
            .setSql("delivered_qty = delivered_qty + " + qtyStr)
            .setSql("fulfilled_qty = fulfilled_qty + " + qtyStr);
        soSalesOrderLineMapper.update(null, wrapper);
        log.info("销售出库回写SO行, soLineId={}, qty={}", soLineId, qtyStr);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProducedQty(Long soLineId, BigDecimal qty) {
        if (soLineId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String qtyStr = qty.toPlainString();
        LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, soLineId)
            .setSql("production_qty = production_qty + " + qtyStr);
        soSalesOrderLineMapper.update(null, wrapper);
        log.info("生产完工回写SO行, soLineId={}, qty={}", soLineId, qtyStr);
    }
}
