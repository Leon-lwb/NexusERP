package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外收货策略实现 (PCBA 闭环重写)
 * <p>
 * 委外完工品入指定(半成品)仓库/库位: 自动创建 wm_batch(来源=委外收货单, 支持外协批次号),
 * 按物料+仓库+库位+批次精确查找或新建库存行, 原子加库存并写流水。</p>
 *
 * @author nexus
 */
@Slf4j
@Component
public class SubcontractReceiveStrategy extends BaseStockOperationStrategy {
    @org.springframework.beans.factory.annotation.Autowired
    private com.nexus.erp.wm.service.WmSerialInventoryService serialInventory;


    /** 事务类型：委外收货 */
    private static final String TX_TYPE_SUBCONTRACT_RECEIVE = "SUBCONTRACT_RECEIVE";
    /** 来源单据类型：委外收货单 */
    private static final String SOURCE_DOC_TYPE = "WM_SUBCONTRACT_RECEIVE";
    /** 批次来源单据类型：委外收货 */
    private static final String BATCH_SOURCE_DOC_TYPE = "SUBCONTRACT_RECEIVE";
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 批次数据访问映射器（Mapper） */
    private final WmBatchMapper batchMapper;
    /** 物料数据访问映射器（Mapper） */
    private final BdMaterialMapper bdMaterialMapper;
    /** 计量单位数据访问映射器（Mapper） */
    private final BdUnitOfMeasureMapper bdUnitOfMeasureMapper;
    /** 仓库数据访问映射器（Mapper） */
    private final WmWarehouseMapper warehouseMapper;
    /** 库位数据访问映射器（Mapper） */
    private final WmStorageLocationMapper locationMapper;
    /** 库区数据访问映射器（Mapper） */
    private final WmStorageAreaMapper areaMapper;

    /**
     * 构造委外收货策略实例
     */
    public SubcontractReceiveStrategy(WmMaterialStockTxMapper stockTxMapper,
                                      WmMaterialStockMapper stockMapper,
                                      WmBatchMapper batchMapper,
                                      BdMaterialMapper bdMaterialMapper,
                                      BdUnitOfMeasureMapper bdUnitOfMeasureMapper,
                                      WmWarehouseMapper warehouseMapper,
                                      WmStorageLocationMapper locationMapper,
                                      WmStorageAreaMapper areaMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.batchMapper = batchMapper;
        this.bdMaterialMapper = bdMaterialMapper;
        this.bdUnitOfMeasureMapper = bdUnitOfMeasureMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.areaMapper = areaMapper;
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
     * 执行委外收货入库
     *
     * @param args args[0]收货单ID, args[1]收货单号, args[2]委外订单ID, args[3]委外单号,
     *             args[4]物料ID, args[5]物料编码, args[6]物料名称, args[7]物料规格,
     *             args[8]单位名称, args[9]供应商ID, args[10]供应商编码, args[11]供应商名称,
     *             args[12]目标仓库ID, args[13]目标库位ID, args[14]外协批次号,
     *             args[15]收货数量, args[16]操作时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long receiveId = (Long) args[0];
        String receiveNo = (String) args[1];
        Long subcontractId = (Long) args[2];
        String subcontractNo = (String) args[3];
        Long materialId = (Long) args[4];
        String materialCode = (String) args[5];
        String materialName = (String) args[6];
        String materialSpec = (String) args[7];
        String baseUomName = (String) args[8];
        Long vendorId = (Long) args[9];
        String vendorCode = (String) args[10];
        String vendorName = (String) args[11];
        Long warehouseId = (Long) args[12];
        Long locationId = (Long) args[13];
        String vendorBatchCode = (String) args[14];
        BigDecimal quantity = (BigDecimal) args[15];
        Date now = (Date) args[16];
        if(serialInventory!=null && serialInventory.post(com.nexus.erp.wm.domain.WmSerialPostingRequest.in("WM_SUBCONTRACT_RECEIVE",receiveId,null,"SUBCONTRACT_RECEIVE",materialId,quantity,warehouseId,locationId,null,null)))return;

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("委外收货数量必须大于0");
        }
        if (warehouseId == null) {
            throw new ServiceException("委外收货必须指定入库仓库");
        }
        if (hasPosted(SOURCE_DOC_TYPE, receiveId)) {
            log.warn("委外收货单[{}]已过账, 跳过重复处理", receiveNo);
            return;
        }

        // 1. 自动创建批次(来源=委外收货, 记录外协批次号), 批次号 = 委外单号-R+收货单尾号
        WmBatch batch = createBatch(subcontractId, subcontractNo, receiveId, receiveNo,
            materialId, vendorId, vendorCode, vendorName, warehouseId, vendorBatchCode, quantity, now);

        // 2. 精确查找或新建库存行(物料+仓库+库位+批次维度)
        WmMaterialStock stock = findOrCreateStock(materialId, materialCode, materialName, materialSpec,
            baseUomName, warehouseId, locationId, batch.getBatchId(), batch.getBatchCode(), quantity, now);

        // 3. 原子加库存
        BigDecimal beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
        int affected = stockMapper.addStock(stock.getStockId(), quantity, stock.getVersion());
        if (affected == 0) {
            throw new ServiceException("库存并发冲突, 请重试: 物料【" + materialName + "】");
        }
        stockMapper.update(null, Wrappers.<WmMaterialStock>lambdaUpdate()
            .set(WmMaterialStock::getRecptDate, now)
            .eq(WmMaterialStock::getStockId, stock.getStockId()));

        // 4. 库存流水
        WmMaterialStock refreshed = stockMapper.selectById(stock.getStockId());
        WmMaterialStockTx tx = buildBaseTxFromStock(refreshed, TX_TYPE_SUBCONTRACT_RECEIVE, TX_FLAG_IN,
            quantity, beforeQty, beforeQty.add(quantity), now);
        tx.setSourceDocType(SOURCE_DOC_TYPE);
        tx.setSourceDocId(receiveId);
        tx.setRemark("委外收货入库-" + subcontractNo + "-" + receiveNo);
        stockTxMapper.insert(tx);

        log.info("委外收货入库完成: 收货单[{}], 委外单[{}], 物料[{}] 数量[{}], 批次[{}], 仓库[{}]",
            receiveNo, subcontractNo, materialCode, quantity.stripTrailingZeros().toPlainString(),
            batch.getBatchCode(), warehouseId);
    }

    /**
     * 创建委外收货批次(wm_batch), 批次号 = 委外单号-R+收货单号尾段
     */
    private WmBatch createBatch(Long subcontractId, String subcontractNo, Long receiveId, String receiveNo,
                                Long materialId, Long vendorId, String vendorCode, String vendorName,
                                Long warehouseId, String vendorBatchCode, BigDecimal quantity, Date now) {
        String tail = receiveNo.contains("-") ? receiveNo.substring(receiveNo.lastIndexOf('-') + 1) : receiveNo;
        String batchCode = subcontractNo + "-R" + tail;
        WmBatch existing = batchMapper.selectOne(Wrappers.<WmBatch>lambdaQuery()
            .eq(WmBatch::getBatchCode, batchCode)
            .eq(WmBatch::getMaterialId, materialId)
            .last("LIMIT 1"));
        if (existing != null) {
            // 同收货单重复过账幂等: 复用既有批次
            return existing;
        }
        BdMaterial material = bdMaterialMapper.selectById(materialId);
        WmWarehouse warehouse = warehouseId != null ? warehouseMapper.selectById(warehouseId) : null;

        WmBatch batch = new WmBatch();
        batch.setBatchCode(batchCode);
        batch.setMaterialId(materialId);
        batch.setMaterialCode(material != null ? material.getMaterialCode() : null);
        batch.setMaterialName(material != null ? material.getMaterialName() : null);
        batch.setMaterialSpec(material != null ? material.getMaterialSpec() : null);
        batch.setVendorId(vendorId);
        batch.setVendorCode(vendorCode);
        batch.setVendorName(vendorName);
        batch.setVendorBatchCode(vendorBatchCode);
        batch.setWarehouseId(warehouseId);
        batch.setWarehouseCode(warehouse != null ? warehouse.getWarehouseCode() : null);
        batch.setWarehouseName(warehouse != null ? warehouse.getWarehouseName() : null);
        batch.setProductionDate(now);
        batch.setQuantity(quantity);
        batch.setReceivedQty(quantity);
        batch.setIssuedQty(BigDecimal.ZERO);
        batch.setStatus("ACTIVE");
        batch.setSourceDocType(BATCH_SOURCE_DOC_TYPE);
        batch.setSourceDocId(receiveId);
        batch.setSourceDocNo(receiveNo);
        batchMapper.insert(batch);
        return batch;
    }

    /**
     * 精确查找或新建库存行(与 ProductionCompletionStrategy 同款维度完整填充)
     */
    private WmMaterialStock findOrCreateStock(Long materialId, String materialCode, String materialName,
                                              String materialSpec, String baseUomName, Long warehouseId,
                                              Long locationId, Long batchId, String batchCode,
                                              BigDecimal quantity, Date now) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        lqw.eq(batchId != null, WmMaterialStock::getBatchId, batchId);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.last("LIMIT 1");
        WmMaterialStock stock = stockMapper.selectOne(lqw);
        if (stock != null) {
            return stock;
        }

        BdMaterial material = bdMaterialMapper.selectById(materialId);
        WmWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        WmStorageLocation location = locationId != null ? locationMapper.selectById(locationId) : null;
        WmStorageArea area = (location != null && location.getAreaId() != null)
            ? areaMapper.selectById(location.getAreaId()) : null;
        BdUnitOfMeasure uom = (material != null && material.getBaseUomId() != null)
            ? bdUnitOfMeasureMapper.selectById(material.getBaseUomId()) : null;

        stock = new WmMaterialStock();
        stock.setMaterialId(materialId);
        stock.setMaterialCode(materialCode);
        stock.setMaterialName(materialName);
        stock.setMaterialSpec(StringUtils.isNotBlank(materialSpec) ? materialSpec
            : (material != null ? material.getMaterialSpec() : null));
        stock.setCategoryId(material != null ? material.getCategoryId() : null);
        stock.setBaseUomId(material != null && material.getBaseUomId() != null
            ? String.valueOf(material.getBaseUomId()) : null);
        stock.setBaseUomName(StringUtils.isNotBlank(baseUomName) ? baseUomName
            : (uom != null ? uom.getUomName() : null));
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
        stock.setStockQuantity(BigDecimal.ZERO);
        stock.setProductionDate(now);
        stock.setRecptDate(now);
        BigDecimal unitCost = material != null && material.getStandardCost() != null
            ? material.getStandardCost() : BigDecimal.ZERO;
        stock.setUnitCost(unitCost);
        stock.setTotalCost(unitCost.multiply(quantity));
        stock.setFrozenFlag(0L);
        stock.setVersion(0);
        stockMapper.insert(stock);
        return stock;
    }
}
