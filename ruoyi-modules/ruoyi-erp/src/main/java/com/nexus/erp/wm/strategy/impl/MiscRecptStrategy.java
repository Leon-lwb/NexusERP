package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 杂项入库策略
 * <p>
 * 处理杂项入库业务，根据杂项入库单行数据增加库存并生成库存流水记录。
 * 支持按物料、批次、库位等维度查询或创建库存记录。
 *
 * @author nexus
 */
@Slf4j
@Component
public class MiscRecptStrategy extends BaseStockOperationStrategy {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;

    /**
     * 事务类型：杂项入库
     */
    private static final String TX_TYPE_MISC_IN = "MISC_IN";

    /**
     * 来源单据类型：杂项入库单
     */
    private static final String SOURCE_DOC_TYPE_MISC_RECPT = "WM_MISC_RECPT";

    /**
     * 事务标志：入库（正数）
     */
    private static final int TX_FLAG_IN = 1;

    /**
     * 库存数据访问映射器（Mapper）
     */
    private final WmMaterialStockMapper stockMapper;

    /**
     * 库存计价服务(F3-a:入库按标准成本计价)
     */
    private final com.nexus.erp.wm.service.IInventoryValuationService valuationService;

    /**
     * 物料基础数据 Mapper(取标准成本)
     */
    private final com.nexus.erp.bd.mapper.BdMaterialMapper materialMapper;

    /**
     * 构造杂项入库策略实例
     *
     * @param stockTxMapper 库存事务Mapper
     * @param stockMapper   库存Mapper
     * @param valuationService 库存计价服务
     * @param materialMapper 物料Mapper
     */
    public MiscRecptStrategy(
            WmMaterialStockTxMapper stockTxMapper,
            WmMaterialStockMapper stockMapper,
            com.nexus.erp.wm.service.IInventoryValuationService valuationService,
            com.nexus.erp.bd.mapper.BdMaterialMapper materialMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.valuationService = valuationService;
        this.materialMapper = materialMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 杂项入库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.MISC_RECPT;
    }

    /**
     * 执行杂项入库操作
     *
     * @param args 操作参数，args[0]为入库单行对象（WmMiscRecptLine），args[1]为入库单对象（WmMiscRecpt），args[2]为操作时间（Date）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        WmMiscRecptLine line = (WmMiscRecptLine) args[0];
        WmMiscRecpt recpt = (WmMiscRecpt) args[1];
        Date now = (Date) args[2];
        String sourceType = line.getPostingSourceType() == null ? SOURCE_DOC_TYPE_MISC_RECPT : line.getPostingSourceType();
        if (!java.util.Set.of(SOURCE_DOC_TYPE_MISC_RECPT, "WM_MATERIAL_RETURN", "WM_MATERIAL_HANDOVER").contains(sourceType)) {
            throw new ServiceException("杂项入库来源类型未授权");
        }
        if (serialInventory != null
                && serialInventory.post(
                        com.nexus.erp.wm.domain.WmSerialPostingRequest.in(
                                sourceType,
                                recpt.getRecptId(),
                                line.getLineId(),
                                "WM_MATERIAL_RETURN".equals(sourceType) ? "RETURN_IN" : "WM_MATERIAL_HANDOVER".equals(sourceType) ? "HANDOVER" : "MISC_RECPT",
                                line.getMaterialId(),
                                line.getQuantity(),
                                recpt.getWarehouseId(),
                                line.getLocationId(),
                                line.getBatchId(),
                                line.getUnitPrice()))) {
            return;
        }

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("杂项入库数量必须大于0");
        }

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        lqw.eq(
                recpt.getWarehouseId() != null,
                WmMaterialStock::getWarehouseId,
                recpt.getWarehouseId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        BigDecimal beforeQuantity =
                stock == null || stock.getStockQuantity() == null
                        ? BigDecimal.ZERO
                        : stock.getStockQuantity();
        BigDecimal afterQuantity = beforeQuantity.add(line.getQuantity());

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(line.getMaterialId());
            stock.setMaterialCode(line.getMaterialCode());
            stock.setMaterialName(line.getMaterialName());
            stock.setMaterialSpec(line.getMaterialSpec());
            stock.setBaseUomId(line.getBaseUomId());
            stock.setBaseUomName(line.getBaseUomName());
            stock.setBatchId(line.getBatchId());
            stock.setBatchCode(line.getBatchCode());
            stock.setWarehouseId(recpt.getWarehouseId());
            stock.setWarehouseCode(recpt.getWarehouseCode());
            stock.setWarehouseName(recpt.getWarehouseName());
            stock.setAreaId(recpt.getAreaId());
            stock.setAreaCode(recpt.getAreaCode());
            stock.setAreaName(recpt.getAreaName());
            stock.setLocationId(line.getLocationId());
            stock.setLocationCode(line.getLocationCode());
            stock.setLocationName(line.getLocationName());
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stock.setVersion(0);
            stockMapper.insert(stock);
        } else {
            stock.setRecptDate(now);
            int affected =
                    stockMapper.addStock(
                            stock.getStockId(), line.getQuantity(), stock.getVersion());
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
            }
        }

        // F3-a:杂项入库按物料标准成本计价(无标准成本按0)
        BigDecimal inUnitCost = resolveMiscUnitCost(line.getMaterialId());
        valuationService.updateStockCost(stock.getStockId(), line.getQuantity(), inUnitCost);
        stock = stockMapper.selectById(stock.getStockId());

        WmMaterialStockTx tx =
                buildBaseTxFromStock(
                        stock,
                        TX_TYPE_MISC_IN,
                        TX_FLAG_IN,
                        line.getQuantity(),
                        beforeQuantity,
                        afterQuantity,
                        now);
        tx.setUnitCost(stock.getUnitCost());
        tx.setSourceDocType(sourceType);
        tx.setSourceDocId(line.getRecptId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("杂项入库");
        stockTxMapper.insert(tx);
    }

    /**
     * 杂项入库计价:优先标准成本,其次参考价,无价格按 0
     */
    private BigDecimal resolveMiscUnitCost(Long materialId) {
        com.nexus.erp.bd.domain.BdMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            return BigDecimal.ZERO;
        }
        if (material.getStandardCost() != null) {
            return material.getStandardCost();
        }
        return material.getReferencePrice() != null
                ? material.getReferencePrice()
                : BigDecimal.ZERO;
    }
}
