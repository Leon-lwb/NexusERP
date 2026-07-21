package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
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
 * <p>
 * 新建库存记录时完整填充物料/仓库/库区/库位/批次/单位/成本等维度，
 * 与采购入库 {@link ReceiveInStrategy} 保持一致，避免库存记录维度缺失。
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

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 生产完工数据访问映射器（Mapper） */
    private final BdMaterialMapper bdMaterialMapper;
    /** 仓库数据访问映射器（Mapper） */
    private final WmWarehouseMapper wmWarehouseMapper;
    /** 存储库位数据访问映射器（Mapper） */
    private final WmStorageLocationMapper wmStorageLocationMapper;
    /** 存储区域数据访问映射器（Mapper） */
    private final WmStorageAreaMapper wmStorageAreaMapper;
    /** 生产完工数据访问映射器（Mapper） */
    private final BdUnitOfMeasureMapper bdUnitOfMeasureMapper;

    /**
     * 构造完工入库策略实例
     *
     * @param stockTxMapper            库存事务Mapper
     * @param stockMapper              库存Mapper
     * @param bdMaterialMapper         物料Mapper（取分类/规格/单位/成本）
     * @param wmWarehouseMapper        仓库Mapper（取仓库编码/名称）
     * @param wmStorageLocationMapper  库位Mapper（取库位编码/名称/库区ID）
     * @param wmStorageAreaMapper      库区Mapper（取库区编码/名称）
     * @param bdUnitOfMeasureMapper    计量单位Mapper（取单位名称）
     */
    public ProductionCompletionStrategy(WmMaterialStockTxMapper stockTxMapper,
                                        WmMaterialStockMapper stockMapper,
                                        BdMaterialMapper bdMaterialMapper,
                                        WmWarehouseMapper wmWarehouseMapper,
                                        WmStorageLocationMapper wmStorageLocationMapper,
                                        WmStorageAreaMapper wmStorageAreaMapper,
                                        BdUnitOfMeasureMapper bdUnitOfMeasureMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.bdMaterialMapper = bdMaterialMapper;
        this.wmWarehouseMapper = wmWarehouseMapper;
        this.wmStorageLocationMapper = wmStorageLocationMapper;
        this.wmStorageAreaMapper = wmStorageAreaMapper;
        this.bdUnitOfMeasureMapper = bdUnitOfMeasureMapper;
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
     * @param args 操作参数，依次为：工单ID、工单编号、物料ID、物料编码、物料名称、入库数量、
     *             仓库ID、库位ID、批次ID、批次号、操作时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long woId = (Long) args[0];
        String woNo = (String) args[1];
        Long materialId = (Long) args[2];
        String materialCode = (String) args[3];
        String materialName = (String) args[4];
        BigDecimal quantity = (BigDecimal) args[5];
        Long warehouseId = (Long) args[6];
        Long locationId = args[7] != null ? (Long) args[7] : null;
        Long batchId = args[8] != null ? (Long) args[8] : null;
        String batchCode = (String) args[9];
        Date now = (Date) args[10];

        // 幂等检查：同一工单不重复入库
        if (hasPosted(SOURCE_DOC_TYPE_WORK_ORDER, woId)) {
            log.warn("工单[{}]已入库，跳过重复处理", woId);
            return;
        }

        // 1. 查询或创建库存记录（新建时完整填充维度字段）
        WmMaterialStock stock = findOrCreateStock(materialId, warehouseId, locationId, batchId, batchCode,
            materialCode, materialName, woId, woNo, quantity, now);

        // 2. 原子增加库存数量
        int affected = stockMapper.addStock(stock.getStockId(), quantity, stock.getVersion());
        if (affected == 0) {
            throw new ServiceException("库存并发冲突，请重试: 物料【" + materialName + "】");
        }
        WmMaterialStock refreshedStock = stockMapper.selectById(stock.getStockId());
        BigDecimal afterQty = refreshedStock.getStockQuantity();
        BigDecimal beforeQty = afterQty.subtract(quantity);

        // 3. 生成库存流水（buildBaseTxFromStock 从 stock 派生全部维度字段）
        WmMaterialStockTx tx = buildBaseTxFromStock(refreshedStock, TX_TYPE_PRODUCTION_IN, TX_FLAG_IN,
            quantity, beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_WORK_ORDER);
        tx.setSourceDocId(woId);
        tx.setRemark("工单完工入库");
        stockTxMapper.insert(tx);

        log.info("工单[{}]完工入库，物料[{}]，数量[{}]", woId, materialCode, quantity.stripTrailingZeros().toPlainString());
    }

    /**
     * 查询或创建库存记录
     * <p>
     * 匹配维度：物料 + 仓库 + 库位 + 批次（批次非空时按批次隔离，与采购入库一致）。
     * 新建时按物料/仓库/库位/库区主数据完整填充维度字段，避免库存记录信息缺失。
     */
    private WmMaterialStock findOrCreateStock(Long materialId, Long warehouseId, Long locationId,
                                               Long batchId, String batchCode,
                                               String materialCode, String materialName,
                                               Long woId, String woNo, BigDecimal quantity, Date now) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        lqw.eq(batchId != null, WmMaterialStock::getBatchId, batchId);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        if (stock == null) {
            stock = buildStock(materialId, warehouseId, locationId, batchId, batchCode,
                materialCode, materialName, woId, woNo, quantity, now);
            stockMapper.insert(stock);
        }
        return stock;
    }

    /**
     * 按主数据构建完整库存记录（新建时）
     * <p>
     * 物料维度取自 bd_material（分类/规格/单位/成本），仓库维度取自 wm_warehouse，
     * 库位/库区维度取自 wm_storage_location + wm_storage_area，单位名称取自 bd_unit_of_measure。
     */
    private WmMaterialStock buildStock(Long materialId, Long warehouseId, Long locationId,
                                        Long batchId, String batchCode,
                                        String materialCode, String materialName,
                                        Long woId, String woNo, BigDecimal quantity, Date now) {
        BdMaterial material = bdMaterialMapper.selectById(materialId);
        WmWarehouse warehouse = warehouseId != null ? wmWarehouseMapper.selectById(warehouseId) : null;
        WmStorageLocation location = locationId != null ? wmStorageLocationMapper.selectById(locationId) : null;
        WmStorageArea area = (location != null && location.getAreaId() != null)
            ? wmStorageAreaMapper.selectById(location.getAreaId()) : null;
        BdUnitOfMeasure uom = (material != null && material.getBaseUomId() != null)
            ? bdUnitOfMeasureMapper.selectById(material.getBaseUomId()) : null;

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(materialId);
        stock.setMaterialCode(materialCode);
        stock.setMaterialName(materialName);
        stock.setMaterialSpec(material != null ? material.getMaterialSpec() : null);
        stock.setCategoryId(material != null ? material.getCategoryId() : null);
        stock.setBaseUomId(material != null && material.getBaseUomId() != null
            ? String.valueOf(material.getBaseUomId()) : null);
        stock.setBaseUomName(uom != null ? uom.getUomName() : null);
        stock.setBatchId(batchId);
        stock.setBatchCode(batchCode);
        stock.setWarehouseId(warehouseId);
        stock.setWarehouseCode(warehouse != null ? warehouse.getWarehouseCode() : null);
        stock.setWarehouseName(warehouse != null ? warehouse.getWarehouseName() : null);
        stock.setAreaId(location != null ? location.getAreaId() : null);
        stock.setAreaCode(area != null ? area.getAreaCode() : null);
        stock.setAreaName(area != null ? area.getAreaName() : null);
        stock.setLocationId(locationId);
        stock.setLocationCode(location != null ? location.getLocationCode() : null);
        stock.setLocationName(location != null ? location.getLocationName() : null);
        stock.setWoId(woId);
        stock.setWoNo(woNo);
        stock.setStockQuantity(BigDecimal.ZERO);
        stock.setProductionDate(now);
        stock.setRecptDate(now);
        BigDecimal unitCost = material != null && material.getStandardCost() != null
            ? material.getStandardCost() : BigDecimal.ZERO;
        stock.setUnitCost(unitCost);
        stock.setTotalCost(unitCost.multiply(quantity != null ? quantity : BigDecimal.ZERO));
        stock.setFrozenFlag(0L);
        stock.setVersion(0);
        return stock;
    }
}
