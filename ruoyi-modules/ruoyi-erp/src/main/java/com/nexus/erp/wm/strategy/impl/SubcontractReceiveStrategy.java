package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.mq.message.SubcontractStockMessage;
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
    private final ReliableMessageProducer messageProducer;

    /**
     * 构造委外收货策略实例
     *
     * @param stockTxMapper      库存事务Mapper
     * @param stockMapper        库存Mapper
     * @param messageProducer    消息生产者
     */
    public SubcontractReceiveStrategy(WmMaterialStockTxMapper stockTxMapper,
                                      WmMaterialStockMapper stockMapper,
                                      ReliableMessageProducer messageProducer) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.messageProducer = messageProducer;
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
     * @param args 操作参数，args[0]为委外订单ID（Long），args[1]为物料ID（Long），
     *             args[2]为收货数量（BigDecimal），args[3]为物料名称（String），
     *             args[4]为委外订单编号（String），args[5]为供应商ID（Long），
     *             args[6]为供应商编码（String），args[7]为供应商名称（String），
     *             args[8]为物料编码（String），args[9]为物料规格（String），
     *             args[10]为基本单位名称（String）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long subcontractId = (Long) args[0];
        Long materialId = (Long) args[1];
        BigDecimal quantity = (BigDecimal) args[2];
        String materialName = (String) args[3];
        String subcontractNo = (String) args[4];
        Long vendorId = (Long) args[5];
        String vendorCode = (String) args[6];
        String vendorName = (String) args[7];
        String materialCode = (String) args[8];
        String materialSpec = (String) args[9];
        String baseUomName = (String) args[10];

        if (hasPosted(SOURCE_DOC_TYPE_SUBCONTRACT + "_RECEIVE", subcontractId)) {
            return;
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("委外收货数量必须大于0");
        }

        Date now = new Date();

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(WmMaterialStock::getVendorId, vendorId);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);

        WmMaterialStock stock = null;
        if (stockList != null && !stockList.isEmpty()) {
            stock = stockList.get(0);
        }

        BigDecimal beforeQty = BigDecimal.ZERO;
        if (stock != null) {
            beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            BigDecimal afterQty = beforeQty.add(quantity);
            stock.setStockQuantity(afterQty);
            stock.setRecptDate(now);
            int affected = stockMapper.updateById(stock);
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + materialName + "】");
            }
        } else {
            stock = new WmMaterialStock();
            stock.setMaterialId(materialId);
            stock.setMaterialCode(materialCode);
            stock.setMaterialName(materialName);
            stock.setMaterialSpec(materialSpec);
            stock.setBaseUomName(baseUomName);
            stock.setVendorId(vendorId);
            stock.setVendorCode(vendorCode);
            stock.setVendorName(vendorName);
            stock.setStockQuantity(quantity);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stockMapper.insert(stock);
        }

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SUBCONTRACT_RECEIVE, TX_FLAG_IN,
            quantity, beforeQty, beforeQty.add(quantity), now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_SUBCONTRACT);
        tx.setSourceDocId(subcontractId);
        tx.setSourceDocCode(subcontractNo);
        tx.setRemark("委外收货入库");
        stockTxMapper.insert(tx);

        SubcontractStockMessage msg = new SubcontractStockMessage();
        msg.setSubcontractId(subcontractId);
        msg.setOperationType("RECEIVE");
        msg.setMaterialId(materialId);
        msg.setQuantity(quantity);
        msg.setSourceModule("wm");
        messageProducer.send(ExchangeConstants.DIRECT_EXCHANGE,
            RoutingKeyConstants.SUBCONTRACT_STOCK, msg);
    }
}
