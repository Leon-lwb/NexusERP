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
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购入库策略
 */
@Slf4j
@Component
public class ReceiveInStrategy extends BaseStockOperationStrategy {

    private static final String SOURCE_DOC_TYPE_RECEIVE = "WM_MATERIAL_RECEIVE";
    private static final String TX_TYPE_RECEIVE_IN = "RECEIVE_IN";
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialReceiveMapper receiveMapper;
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    private final WmMaterialStockMapper stockMapper;

    public ReceiveInStrategy(WmMaterialStockTxMapper stockTxMapper,
                             WmMaterialReceiveMapper receiveMapper,
                             WmMaterialReceiveDetailMapper receiveDetailMapper,
                             WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.receiveMapper = receiveMapper;
        this.receiveDetailMapper = receiveDetailMapper;
        this.stockMapper = stockMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        return StockOperationTypeEnum.RECEIVE_IN;
    }

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
                stockMapper.insert(stock);
            } else {
                stock.setStockQuantity(afterQuantity);
                stock.setRecptDate(now);
                stockMapper.updateById(stock);
            }

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
        return stock;
    }

    private WmMaterialStockTx buildReceiveStockTx(WmMaterialReceive receive, WmMaterialReceiveDetail detail,
                                                  WmMaterialStock stock, BigDecimal beforeQuantity,
                                                  BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_RECEIVE_IN, TX_FLAG_IN, detail.getQuantity(),
            beforeQuantity, afterQuantity, now);
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
