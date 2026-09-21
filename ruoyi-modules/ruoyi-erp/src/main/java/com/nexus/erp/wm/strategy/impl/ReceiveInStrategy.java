package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购入库策略
 * <p>
 * 处理物料采购入库业务，根据入库单明细增加库存数量并生成库存流水记录。
 * 支持按物料、批次、仓库、库位等维度精确匹配库存记录。
 *
 * @author nexus
 */
@Slf4j
@Component
public class ReceiveInStrategy extends BaseStockOperationStrategy {

    /** 来源单据类型：物料入库单 */
    private static final String SOURCE_DOC_TYPE_RECEIVE = "WM_MATERIAL_RECEIVE";
    /** 事务类型：采购入库 */
    private static final String TX_TYPE_RECEIVE_IN = "RECEIVE_IN";
    /** 事务标志：入库（正数） */
    private static final int TX_FLAG_IN = 1;

    /** 领用数据访问映射器（Mapper） */
    private final WmMaterialReceiveMapper receiveMapper;
    /** 领用明细数据访问映射器（Mapper） */
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 库存计价服务(F3-a:入库按采购参考价/标准成本计价) */
    private final IInventoryValuationService valuationService;
    /** 物料基础数据 Mapper(取参考价/标准成本) */
    private final com.nexus.erp.bd.mapper.BdMaterialMapper materialMapper;

    /**
     * 构造采购入库策略实例
     *
     * @param stockTxMapper        库存事务Mapper
     * @param receiveMapper        物料入库单Mapper
     * @param receiveDetailMapper  入库单明细Mapper
     * @param stockMapper          库存Mapper
     * @param valuationService     库存计价服务
     * @param materialMapper       物料Mapper
     */
    public ReceiveInStrategy(WmMaterialStockTxMapper stockTxMapper,
                             WmMaterialReceiveMapper receiveMapper,
                             WmMaterialReceiveDetailMapper receiveDetailMapper,
                             WmMaterialStockMapper stockMapper,
                             IInventoryValuationService valuationService,
                             com.nexus.erp.bd.mapper.BdMaterialMapper materialMapper) {
        super(stockTxMapper);
        this.receiveMapper = receiveMapper;
        this.receiveDetailMapper = receiveDetailMapper;
        this.stockMapper = stockMapper;
        this.valuationService = valuationService;
        this.materialMapper = materialMapper;
    }

    /** 入库计价:优先参考价,其次标准成本,无价格按 0 */
    private BigDecimal resolveReceiveUnitCost(WmMaterialReceiveDetail detail) {
        com.nexus.erp.bd.domain.BdMaterial material = materialMapper.selectById(detail.getMaterialId());
        if (material == null) {
            return BigDecimal.ZERO;
        }
        if (material.getReferencePrice() != null) {
            return material.getReferencePrice();
        }
        return material.getStandardCost() != null ? material.getStandardCost() : BigDecimal.ZERO;
    }

    /**
     * 获取策略支持的操作类型
     *
     * @return 采购入库操作类型枚举
     */
    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.RECEIVE_IN;
    }

    /**
     * 执行采购入库操作
     *
     * @param args 操作参数，args[0]为入库单ID（Long）
     */
    @Override
    public void execute(Object... args) {
        Long receiveId = (Long) args[0];
        WmMaterialReceive receive = receiveMapper.selectById(receiveId);
        if (receive == null) {
            throw new RuntimeException("入库单不存在");
        }
        if (hasPosted(SOURCE_DOC_TYPE_RECEIVE, receiveId)) {
            return;
        }

        List<WmMaterialReceiveDetail> detailList = receiveDetailMapper.selectList(
            Wrappers.lambdaQuery(WmMaterialReceiveDetail.class)
                .eq(WmMaterialReceiveDetail::getReceiveId, receiveId));
        if (detailList == null || detailList.isEmpty()) {
            throw new RuntimeException("入库单明细为空，无法入库过账");
        }

        Date now = new Date();
        for (WmMaterialReceiveDetail detail : detailList) {
            if (detail.getQuantity() == null || detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("物料【" + detail.getMaterialName() + "】入库明细数量必须大于0");
            }
            WmMaterialStock stock = findStockForReceive(detail, receive);
            BigDecimal beforeQuantity = stock == null || stock.getStockQuantity() == null
                ? BigDecimal.ZERO : stock.getStockQuantity();
            BigDecimal afterQuantity = beforeQuantity.add(detail.getQuantity());

            if (stock == null) {
                stock = buildStockFromReceiveDetail(detail, receive, afterQuantity, now);
                try {
                    stockMapper.insert(stock);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // ERP-WM-08: 「查不到就 insert」在并发收货 / MQ 重投下是典型 TOCTOU ——
                    // 两个线程都查到 null 各自插入，产生同维度重复库存行，此后该物料所有收货
                    // 都会因「匹配到多条库存记录」永久失败。唯一键 uk_stock_dim 生效后由数据库兜底，
                    // 这里捕获冲突后重查已存在的行，改为原子累加。
                    WmMaterialStock existing = findStockForReceive(detail, receive);
                    if (existing == null) {
                        throw e;
                    }
                    log.warn("收货并发插入库存行冲突, 已改为复用既有行: materialId={}, batchId={}, locationId={}, stockId={}",
                        detail.getMaterialId(), detail.getBatchId(), detail.getLocationId(), existing.getStockId());
                    stock = existing;
                    beforeQuantity = existing.getStockQuantity() == null
                        ? BigDecimal.ZERO : existing.getStockQuantity();
                    afterQuantity = beforeQuantity.add(detail.getQuantity());
                    int affected = stockMapper.addStock(stock.getStockId(), detail.getQuantity(), stock.getVersion());
                    if (affected == 0) {
                        throw new com.nexus.common.core.exception.ServiceException("库存并发冲突，请重试");
                    }
                }
            } else {
                int affected = stockMapper.addStock(stock.getStockId(), detail.getQuantity(), stock.getVersion());
                if (affected == 0) {
                    throw new com.nexus.common.core.exception.ServiceException("库存并发冲突，请重试");
                }
                stockMapper.update(null, Wrappers.<WmMaterialStock>lambdaUpdate()
                    .set(WmMaterialStock::getRecptDate, now)
                    .eq(WmMaterialStock::getStockId, stock.getStockId()));
                stock = stockMapper.selectById(stock.getStockId());
            }

            // F3-a:采购入库按参考价/标准成本计价(明细无单价,取物料价格;无价格按0)
            BigDecimal inUnitCost = resolveReceiveUnitCost(detail);
            valuationService.updateStockCost(stock.getStockId(), detail.getQuantity(), inUnitCost);
            stock = stockMapper.selectById(stock.getStockId());

            stockTxMapper.insert(buildReceiveStockTx(receive, detail, stock, beforeQuantity, afterQuantity, now));
        }
    }

    private WmMaterialStock findStockForReceive(WmMaterialReceiveDetail detail, WmMaterialReceive receive) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, detail.getMaterialId());
        lqw.eq(detail.getBaseUomId() != null, WmMaterialStock::getBaseUomId, detail.getBaseUomId());
        lqw.eq(detail.getBatchId() != null, WmMaterialStock::getBatchId, detail.getBatchId());
        lqw.eq(detail.getWarehouseId() != null, WmMaterialStock::getWarehouseId, detail.getWarehouseId());
        lqw.eq(detail.getAreaId() != null, WmMaterialStock::getAreaId, detail.getAreaId());
        lqw.eq(detail.getLocationId() != null, WmMaterialStock::getLocationId, detail.getLocationId());
        lqw.eq(receive.getVendorId() != null, WmMaterialStock::getVendorId, receive.getVendorId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            return null;
        }
        if (stockList.size() > 1) {
            throw new RuntimeException("物料【" + detail.getMaterialName() + "】匹配到多条库存记录，请检查库存维度");
        }
        return stockList.get(0);
    }

    private WmMaterialStock buildStockFromReceiveDetail(WmMaterialReceiveDetail detail, WmMaterialReceive receive,
                                                        BigDecimal stockQuantity, Date recptDate) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(detail.getMaterialId());
        stock.setMaterialCode(detail.getMaterialCode());
        stock.setMaterialName(detail.getMaterialName());
        stock.setMaterialSpec(detail.getMaterialSpec());
        stock.setBaseUomId(detail.getBaseUomId());
        stock.setBaseUomName(detail.getBaseUomName());
        stock.setBatchId(detail.getBatchId());
        stock.setBatchCode(detail.getBatchCode());
        stock.setWarehouseId(detail.getWarehouseId());
        stock.setWarehouseCode(detail.getWarehouseCode());
        stock.setWarehouseName(detail.getWarehouseName());
        stock.setAreaId(detail.getAreaId());
        stock.setAreaCode(detail.getAreaCode());
        stock.setAreaName(detail.getAreaName());
        stock.setLocationId(detail.getLocationId());
        stock.setLocationCode(detail.getLocationCode());
        stock.setLocationName(detail.getLocationName());
        stock.setVendorId(receive.getVendorId());
        stock.setVendorCode(receive.getVendorCode());
        stock.setVendorName(receive.getVendorName());
        stock.setVendorNick(receive.getVendorNick());
        stock.setStockQuantity(stockQuantity);
        stock.setRecptDate(recptDate);
        stock.setFrozenFlag(0L);
        stock.setVersion(0);
        return stock;
    }

    private WmMaterialStockTx buildReceiveStockTx(WmMaterialReceive receive, WmMaterialReceiveDetail detail,
                                                  WmMaterialStock stock, BigDecimal beforeQuantity,
                                                  BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_RECEIVE_IN, TX_FLAG_IN, detail.getQuantity(),
            beforeQuantity, afterQuantity, now);
        tx.setUnitCost(stock.getUnitCost());
        tx.setSourceDocType(SOURCE_DOC_TYPE_RECEIVE);
        tx.setSourceDocId(receive.getReceiveId());
        tx.setSourceDocCode(receive.getReceiveCode());
        tx.setSourceDocLineId(detail.getLineId());
        tx.setErpDate(receive.getReceiveDate());
        tx.setRecptDate(now);
        tx.setRemark("物料入库单过账入库");
        return tx;
    }
}
