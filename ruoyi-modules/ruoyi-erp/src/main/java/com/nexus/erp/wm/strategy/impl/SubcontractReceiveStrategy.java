package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 委外收货策略实现
 * <p>
 * 处理委外订单收货入库业务，增加库存并记录事务流水。
 * 支持按物料和供应商维度查询或创建库存记录，并处理库存并发冲突。
 *
 * @author nexus
 */
@Slf4j
@Component
public class SubcontractReceiveStrategy extends BaseStockOperationStrategy {

    /** 事务类型：委外收货 */
    private static final String TX_TYPE_SUBCONTRACT_RECEIVE = "SUBCONTRACT_RECEIVE";
    /** 来源单据类型：委外订单 */
    private static final String SOURCE_DOC_TYPE_SUBCONTRACT = "PRD_SUBCONTRACT";
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;
    private final PrdSubcontractMapper subcontractMapper;

    /**
     * 构造委外收货策略实例
     *
     * @param stockTxMapper      库存事务Mapper
     * @param stockMapper        库存Mapper
     * @param subcontractMapper  委外订单Mapper
     */
    public SubcontractReceiveStrategy(WmMaterialStockTxMapper stockTxMapper,
                                      WmMaterialStockMapper stockMapper,
                                      PrdSubcontractMapper subcontractMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.subcontractMapper = subcontractMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 委外收货操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.SUBCONTRACT_RECEIVE;
    }

    /**
     * 执行委外收货入库操作
     *
     * @param args 操作参数，args[0]为委外订单ID（Long）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long subcontractId = (Long) args[0];
        PrdSubcontract subcontract = subcontractMapper.selectById(subcontractId);
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        if (hasPosted(SOURCE_DOC_TYPE_SUBCONTRACT + "_RECEIVE", subcontractId)) {
            return;
        }

        BigDecimal receiveQty = subcontract.getActualQty() != null ? subcontract.getActualQty() : subcontract.getPlannedQty();
        if (receiveQty == null || receiveQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("委外收货数量必须大于0");
        }

        Date now = new Date();

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, subcontract.getMaterialId());
        lqw.eq(WmMaterialStock::getVendorId, subcontract.getVendorId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);

        WmMaterialStock stock = null;
        if (stockList != null && !stockList.isEmpty()) {
            stock = stockList.get(0);
        }

        BigDecimal beforeQty = BigDecimal.ZERO;
        if (stock != null) {
            beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            BigDecimal afterQty = beforeQty.add(receiveQty);
            stock.setStockQuantity(afterQty);
            stock.setRecptDate(now);
            int affected = stockMapper.updateById(stock);
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + subcontract.getMaterialName() + "】");
            }
        } else {
            stock = new WmMaterialStock();
            stock.setMaterialId(subcontract.getMaterialId());
            stock.setMaterialCode(subcontract.getMaterialCode());
            stock.setMaterialName(subcontract.getMaterialName());
            stock.setMaterialSpec(subcontract.getMaterialSpec());
            stock.setBaseUomName(subcontract.getBaseUomName());
            stock.setVendorId(subcontract.getVendorId());
            stock.setVendorCode(subcontract.getVendorCode());
            stock.setVendorName(subcontract.getVendorName());
            stock.setStockQuantity(receiveQty);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stockMapper.insert(stock);
        }

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SUBCONTRACT_RECEIVE, TX_FLAG_IN,
            receiveQty, beforeQty, beforeQty.add(receiveQty), now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_SUBCONTRACT);
        tx.setSourceDocId(subcontract.getSubcontractId());
        tx.setSourceDocCode(subcontract.getSubcontractNo());
        tx.setRemark("委外收货入库");
        stockTxMapper.insert(tx);
    }
}
