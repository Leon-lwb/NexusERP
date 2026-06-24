package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 完工入库策略
 * <p>
 * 工单完工时，将成品/半成品入库到指定仓库/库位。
 * 支持查询或创建库存记录，并生成对应的库存事务流水。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ProductionCompletionStrategy extends BaseStockOperationStrategy {

    /** 事务类型：生产入库 */
    private static final String TX_TYPE_PRODUCTION_IN = "PRODUCTION_IN";
    /** 来源单据类型：生产工单 */
    private static final String SOURCE_DOC_TYPE_WORK_ORDER = "PRD_WORK_ORDER";
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;

    /**
     * 构造完工入库策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     */
    public ProductionCompletionStrategy(WmMaterialStockTxMapper stockTxMapper,
                                        WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.stockTxMapper = stockTxMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 生产入库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.PRODUCTION_IN;
    }

    /**
     * 执行完工入库操作
     *
     * @param args 操作参数，依次为：工单ID、物料ID、物料编码、物料名称、入库数量、仓库ID、库位ID、操作时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long woId = (Long) args[0];
        Long materialId = (Long) args[1];
        String materialCode = (String) args[2];
        String materialName = (String) args[3];
        BigDecimal quantity = (BigDecimal) args[4];
        Long warehouseId = (Long) args[5];
        Long locationId = args[6] != null ? (Long) args[6] : null;
        Date now = (Date) args[7];

        // 幂等检查：同一工单不重复入库
        if (hasPosted(SOURCE_DOC_TYPE_WORK_ORDER, woId)) {
            log.warn("工单[{}]已入库，跳过重复处理", woId);
            return;
        }

        // 1. 查询或创建库存记录
        WmMaterialStock stock = findOrCreateStock(materialId, warehouseId, locationId, materialCode, materialName);

        // 2. 原子增加库存数量
        int affected = stockMapper.addStock(stock.getStockId(), quantity, stock.getVersion());
        if (affected == 0) {
            throw new ServiceException("库存并发冲突，请重试: 物料【" + materialName + "】");
        }
        WmMaterialStock refreshedStock = stockMapper.selectById(stock.getStockId());
        BigDecimal afterQty = refreshedStock.getStockQuantity();
        BigDecimal beforeQty = afterQty.subtract(quantity);

        // 3. 生成库存流水
        WmMaterialStockTx tx = buildBaseTxFromStock(refreshedStock, TX_TYPE_PRODUCTION_IN, TX_FLAG_IN,
            quantity, beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_WORK_ORDER);
        tx.setSourceDocId(woId);
        tx.setRemark("工单完工入库");
        stockTxMapper.insert(tx);

        log.info("工单[{}]完工入库，物料[{}]，数量[{}]", woId, materialCode, quantity.stripTrailingZeros().toPlainString());
    }

    private WmMaterialStock findOrCreateStock(Long materialId, Long warehouseId, Long locationId,
                                               String materialCode, String materialName) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(materialId);
            stock.setMaterialCode(materialCode);
            stock.setMaterialName(materialName);
            stock.setWarehouseId(warehouseId);
            stock.setLocationId(locationId);
            stock.setStockQuantity(BigDecimal.ZERO);
            //stock.setIsQualityInspected("N");
            stockMapper.insert(stock);
        }
        return stock;
    }
}
