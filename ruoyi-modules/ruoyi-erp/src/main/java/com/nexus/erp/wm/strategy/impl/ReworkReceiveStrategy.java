package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.mq.message.ReworkStockMessage;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 返工入库策略实现
 * <p>
 * 处理返工订单完工入库业务，增加库存并记录事务流水。
 * 支持查询或创建库存记录，并处理库存并发冲突。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ReworkReceiveStrategy extends BaseStockOperationStrategy {

    /** 事务类型：返工入库 */
    private static final String TX_TYPE_REWORK_RECEIVE = "REWORK_RECEIVE";
    /** 来源单据类型：返工入库（与返工出库 PRD_REWORK_ORDER 区分，保证幂等键互不干扰） */
    private static final String SOURCE_DOC_TYPE_REWORK_RECEIVE = "PRD_REWORK_ORDER_RECEIVE";
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 消息提供器 */
    private final ReliableMessageProducer messageProducer;
    /** 上架策略服务（仓库维度缺省时按规则解析目标仓库） */
    private final IPutawayStrategyService putawayStrategyService;
    /** 物料主数据 Mapper（新建库存行补全规格/单位维度） */
    private final BdMaterialMapper bdMaterialMapper;
    /** 计量单位 Mapper（新建库存行补全单位名称） */
    private final BdUnitOfMeasureMapper bdUnitOfMeasureMapper;
    /** 仓库 Mapper（新建库存行补全仓库编码名称） */
    private final WmWarehouseMapper wmWarehouseMapper;
    /** 库位 Mapper（新建库存行补全库位/库区维度） */
    private final WmStorageLocationMapper wmStorageLocationMapper;
    /** 库区 Mapper（新建库存行补全库区维度） */
    private final WmStorageAreaMapper wmStorageAreaMapper;

    /**
     * 构造返工入库策略实例
     */
    public ReworkReceiveStrategy(WmMaterialStockTxMapper stockTxMapper,
                                 WmMaterialStockMapper stockMapper,
                                 ReliableMessageProducer messageProducer,
                                 IPutawayStrategyService putawayStrategyService,
                                 BdMaterialMapper bdMaterialMapper,
                                 BdUnitOfMeasureMapper bdUnitOfMeasureMapper,
                                 WmWarehouseMapper wmWarehouseMapper,
                                 WmStorageLocationMapper wmStorageLocationMapper,
                                 WmStorageAreaMapper wmStorageAreaMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.messageProducer = messageProducer;
        this.putawayStrategyService = putawayStrategyService;
        this.bdMaterialMapper = bdMaterialMapper;
        this.bdUnitOfMeasureMapper = bdUnitOfMeasureMapper;
        this.wmWarehouseMapper = wmWarehouseMapper;
        this.wmStorageLocationMapper = wmStorageLocationMapper;
        this.wmStorageAreaMapper = wmStorageAreaMapper;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 返工入库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.REWORK_RECEIVE;
    }

    /**
     * 执行返工入库操作
     * <p>参数契约与 {@code IWmStockOperationService#postReworkReceive} 一致(9参):
     * args[0]=reworkId, args[1]=reworkNo, args[2]=materialId, args[3]=materialCode,
     * args[4]=materialName, args[5]=quantity, args[6]=warehouseId(可空), args[7]=locationId(可空), args[8]=now。
     * 仓库维度: 调用方指定优先, 缺省时按上架规则解析(与完工入库同源), 解析失败回退历史"物料任意仓库"行为。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Object... args) {
        Long reworkId = (Long) args[0];
        String reworkNo = (String) args[1];
        Long materialId = (Long) args[2];
        String materialCode = (String) args[3];
        String materialName = (String) args[4];
        BigDecimal quantity = (BigDecimal) args[5];
        Long warehouseId = (Long) args[6];
        Long locationId = (Long) args[7];
        Date now = args[8] instanceof Date ? (Date) args[8] : new Date();

        if (hasPosted(SOURCE_DOC_TYPE_REWORK_RECEIVE, reworkId)) {
            return;
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("返工入库数量必须大于0");
        }

        // 仓库维度解析: 调用方指定 > 上架规则 > 不限仓库(历史行为)
        PutawayLocation putaway = null;
        if (warehouseId == null) {
            try {
                putaway = putawayStrategyService.resolvePutaway(materialId, quantity);
                warehouseId = putaway.getWarehouseId();
                if (locationId == null) {
                    locationId = putaway.getLocationId();
                }
            } catch (Exception e) {
                log.info("返工入库未解析到上架规则目标仓库, 回退不限仓库: materialId={}, 原因={}", materialId, e.getMessage());
            }
        }

        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);

        WmMaterialStock stock = null;
        if (stockList != null && !stockList.isEmpty()) {
            stock = stockList.get(0);
        }

        BigDecimal beforeQty = BigDecimal.ZERO;
        if (stock != null) {
            beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            int affected = stockMapper.addStock(stock.getStockId(), quantity, stock.getVersion());
            if (affected == 0) {
                throw new ServiceException("库存并发冲突，请重试: 物料【" + materialCode + "/" + materialName + "】");
            }
            stock = stockMapper.selectById(stock.getStockId());
        } else {
            // 新建库存行按主数据完整补全维度(参照 ProductionCompletionStrategy.buildStock)
            stock = buildReworkStock(materialId, materialCode, materialName, quantity, now,
                warehouseId, locationId, putaway);
            stockMapper.insert(stock);
        }

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_REWORK_RECEIVE, TX_FLAG_IN,
            quantity, beforeQty, beforeQty.add(quantity), now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_REWORK_RECEIVE);
        tx.setSourceDocId(reworkId);
        tx.setSourceDocCode(reworkNo);
        tx.setRemark("返工入库");
        stockTxMapper.insert(tx);

        ReworkStockMessage msg = new ReworkStockMessage();
        msg.setReworkId(reworkId);
        msg.setOperationType("RECEIVE");
        msg.setMaterialId(materialId);
        msg.setQuantity(quantity);
        msg.setSourceModule("wm");
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
            RoutingKeyConstants.REWORK_STOCK, msg);
    }

    /**
     * 按主数据构建完整库存行（新建时）
     * <p>物料维度取自 bd_material（规格/单位），仓库/库位/库区维度取自 wm 主数据；
     * 上架规则解析结果携带的编码名称优先于按 ID 反查。批次维度无来源（返工单无批次字段），暂不填。</p>
     */
    private WmMaterialStock buildReworkStock(Long materialId, String materialCode, String materialName,
                                             BigDecimal quantity, Date now,
                                             Long warehouseId, Long locationId, PutawayLocation putaway) {
        BdMaterial material = bdMaterialMapper.selectById(materialId);
        WmWarehouse warehouse = warehouseId != null ? wmWarehouseMapper.selectById(warehouseId) : null;
        WmStorageLocation location = locationId != null ? wmStorageLocationMapper.selectById(locationId) : null;
        WmStorageArea area = (location != null && location.getAreaId() != null)
            ? wmStorageAreaMapper.selectById(location.getAreaId()) : null;
        BdUnitOfMeasure uom = (material != null && material.getBaseUomId() != null)
            ? bdUnitOfMeasureMapper.selectById(material.getBaseUomId()) : null;
        boolean fromPutaway = putaway != null && warehouseId != null && warehouseId.equals(putaway.getWarehouseId());

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(materialId);
        stock.setMaterialCode(materialCode);
        stock.setMaterialName(materialName);
        stock.setMaterialSpec(material != null ? material.getMaterialSpec() : null);
        stock.setBaseUomId(material != null && material.getBaseUomId() != null
            ? String.valueOf(material.getBaseUomId()) : null);
        stock.setBaseUomName(uom != null ? uom.getUomName() : null);
        stock.setWarehouseId(warehouseId);
        stock.setWarehouseCode(fromPutaway ? putaway.getWarehouseCode()
            : (warehouse != null ? warehouse.getWarehouseCode() : null));
        stock.setWarehouseName(fromPutaway ? putaway.getWarehouseName()
            : (warehouse != null ? warehouse.getWarehouseName() : null));
        stock.setAreaId(location != null ? location.getAreaId() : null);
        stock.setAreaCode(area != null ? area.getAreaCode() : null);
        stock.setAreaName(area != null ? area.getAreaName() : null);
        stock.setLocationId(locationId);
        stock.setLocationCode(location != null ? location.getLocationCode() : null);
        stock.setLocationName(location != null ? location.getLocationName() : null);
        stock.setStockQuantity(quantity);
        stock.setRecptDate(now);
        stock.setFrozenFlag(0L);
        stock.setVersion(0);
        return stock;
    }
}
