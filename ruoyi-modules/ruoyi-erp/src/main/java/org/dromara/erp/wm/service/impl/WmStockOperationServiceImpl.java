package org.dromara.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.erp.wm.domain.WmMaterialReceive;
import org.dromara.erp.wm.domain.WmMaterialReceiveDetail;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.domain.WmMiscIssueLine;
import org.dromara.erp.wm.domain.WmMiscRecpt;
import org.dromara.erp.wm.domain.WmMiscRecptLine;
import org.dromara.erp.wm.domain.WmSalesIssue;
import org.dromara.erp.wm.domain.WmSalesIssueLine;
import org.dromara.erp.wm.domain.WmStockTakingLine;
import org.dromara.erp.wm.domain.WmTransferLine;
import org.dromara.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import org.dromara.erp.wm.mapper.WmMaterialReceiveMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockTxMapper;
import org.dromara.erp.wm.mapper.WmSalesIssueLineMapper;
import org.dromara.erp.wm.mapper.WmSalesIssueMapper;
import org.dromara.erp.wm.service.IWmStockOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存过账服务实现
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStockOperationServiceImpl implements IWmStockOperationService {

    private static final String SOURCE_DOC_TYPE_RECEIVE = "WM_MATERIAL_RECEIVE";
    private static final String SOURCE_DOC_TYPE_SALES_ISSUE = "WM_SALES_ISSUE";
    private static final String TX_TYPE_RECEIVE_IN = "RECEIVE_IN";
    private static final String TX_TYPE_SALES_OUT = "SALES_OUT";
    private static final String TX_TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    private static final String TX_TYPE_TRANSFER_IN = "TRANSFER_IN";
    private static final String SOURCE_DOC_TYPE_TRANSFER = "WM_TRANSFER";
    private static final String SOURCE_DOC_TYPE_STOCK_TAKING = "WM_STOCK_TAKING";
    private static final String TX_TYPE_COUNT_ADJUST = "COUNT_ADJUST";
    private static final String SOURCE_DOC_TYPE_MISC_ISSUE = "WM_MISC_ISSUE";
    private static final String SOURCE_DOC_TYPE_MISC_RECPT = "WM_MISC_RECPT";
    private static final String TX_TYPE_MISC_IN = "MISC_IN";
    private static final String TX_TYPE_MISC_OUT = "MISC_OUT";
    private static final int TX_FLAG_IN = 1;
    private static final int TX_FLAG_OUT = -1;

    private final WmMaterialReceiveMapper receiveMapper;
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    private final WmSalesIssueMapper salesIssueMapper;
    private final WmSalesIssueLineMapper salesIssueLineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postMaterialReceive(Long receiveId) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = salesIssueMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (hasPosted(SOURCE_DOC_TYPE_SALES_ISSUE, issueId)) {
            return;
        }

        List<WmSalesIssueLine> lineList = salesIssueLineMapper.selectList(
            Wrappers.lambdaQuery(WmSalesIssueLine.class)
                .eq(WmSalesIssueLine::getIssueId, issueId));
        if (lineList == null || lineList.isEmpty()) {
            throw new RuntimeException("出库单明细为空");
        }

        Date now = new Date();
        for (WmSalesIssueLine line : lineList) {
            if (line.getQuantityIssue() == null || line.getQuantityIssue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】出库数量必须大于0");
            }

            List<WmMaterialStock> stockList = resolveSalesIssueStocks(salesIssue, line);
            BigDecimal remaining = line.getQuantityIssue();

            for (WmMaterialStock stock : stockList) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
                if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal deductQty = stockQty.min(remaining);
                BigDecimal beforeQuantity = stockQty;
                BigDecimal afterQuantity = stockQty.subtract(deductQty);
                stock.setStockQuantity(afterQuantity);
                stockMapper.updateById(stock);
                stockTxMapper.insert(buildSalesOutStockTx(salesIssue, line, stock, beforeQuantity, afterQuantity, now));
                remaining = remaining.subtract(deductQty);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postTransfer(WmTransferLine line) {
        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("调拨数量必须大于0");
        }

        Date now = new Date();

        // 1. 来源库存扣减
        LambdaQueryWrapper<WmMaterialStock> fromLqw = Wrappers.lambdaQuery();
        fromLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        fromLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        fromLqw.eq(line.getFromLocationId() != null, WmMaterialStock::getLocationId, line.getFromLocationId());
        fromLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        fromLqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> fromStocks = stockMapper.selectList(fromLqw);
        if (fromStocks == null || fromStocks.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】来源库存不存在");
        }

        // FIFO扣减：从多个库存记录中扣减直到满足数量
        BigDecimal remaining = line.getQuantity();
        for (WmMaterialStock fromStock : fromStocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = fromStock.getStockQuantity() == null ? BigDecimal.ZERO : fromStock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = stockQty.min(remaining);
            BigDecimal beforeQty = stockQty;
            BigDecimal afterQty = stockQty.subtract(deductQty);
            fromStock.setStockQuantity(afterQty);
            stockMapper.updateById(fromStock);
            stockTxMapper.insert(buildTransferOutTx(line, fromStock, deductQty, beforeQty, afterQty, now));
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】来源库存不足");
        }

        // 2. 目标库存增加
        LambdaQueryWrapper<WmMaterialStock> toLqw = Wrappers.lambdaQuery();
        toLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        toLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        toLqw.eq(line.getToLocationId() != null, WmMaterialStock::getLocationId, line.getToLocationId());
        toLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> toStocks = stockMapper.selectList(toLqw);

        WmMaterialStock toStock;
        if (toStocks == null || toStocks.isEmpty()) {
            // 新建目标库存记录：复制来源第一条库存的属性
            WmMaterialStock fromRef = fromStocks.get(0);
            toStock = new WmMaterialStock();
            toStock.setMaterialId(fromRef.getMaterialId());
            toStock.setMaterialCode(fromRef.getMaterialCode());
            toStock.setMaterialName(fromRef.getMaterialName());
            toStock.setMaterialSpec(fromRef.getMaterialSpec());
            toStock.setBaseUomId(fromRef.getBaseUomId());
            toStock.setBaseUomName(fromRef.getBaseUomName());
            toStock.setBatchId(line.getBatchId());
            toStock.setBatchCode(line.getBatchCode());
            toStock.setWarehouseId(fromRef.getWarehouseId());
            toStock.setWarehouseCode(fromRef.getWarehouseCode());
            toStock.setWarehouseName(fromRef.getWarehouseName());
            toStock.setAreaId(fromRef.getAreaId());
            toStock.setAreaCode(fromRef.getAreaCode());
            toStock.setAreaName(fromRef.getAreaName());
            toStock.setLocationId(line.getToLocationId());
            toStock.setLocationCode(line.getToLocationCode());
            toStock.setLocationName(line.getToLocationName());
            toStock.setStockQuantity(line.getQuantity());
            toStock.setRecptDate(now);
            toStock.setFrozenFlag(0L);
            stockMapper.insert(toStock);
        } else {
            toStock = toStocks.get(0);
            toStock.setStockQuantity(toStock.getStockQuantity().add(line.getQuantity()));
            stockMapper.updateById(toStock);
        }

        stockTxMapper.insert(buildTransferInTx(line, toStock, line.getQuantity(), now));
    }

    @Override
    public void postCountAdjust(WmStockTakingLine line, BigDecimal actualQuantity,
                                BigDecimal bookQuantity, BigDecimal diffQuantity, Date now) {
        int txFlag = diffQuantity.compareTo(BigDecimal.ZERO) > 0 ? TX_FLAG_IN : TX_FLAG_OUT;
        BigDecimal absDiff = diffQuantity.abs();

        WmMaterialStock stock;
        if (line.getStockId() != null) {
            stock = stockMapper.selectById(line.getStockId());
        } else {
            LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
            lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
            lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
            lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
            stock = stockMapper.selectOne(lqw);
        }

        if (stock == null) {
            throw new RuntimeException("盘点物料【" + line.getMaterialName() + "】找不到对应的库存记录");
        }

        BigDecimal beforeQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
        BigDecimal afterQty = bookQuantity.add(diffQuantity);

        stock.setStockQuantity(afterQty);
        stockMapper.updateById(stock);

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_COUNT_ADJUST, txFlag, absDiff,
            beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_STOCK_TAKING);
        tx.setSourceDocId(line.getPlanId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("盘点调整，账面:" + bookQuantity + " 实际:" + actualQuantity + " 差异:" + diffQuantity);
        stockTxMapper.insert(tx);
    }

    @Override
    public void postMiscIssue(WmMiscIssueLine line, Date now) {
        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("杂项出库数量必须大于0");
        }

        List<WmMaterialStock> stockList = resolveMiscIssueStocks(line);
        BigDecimal remaining = line.getQuantity();

        for (WmMaterialStock stock : stockList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal stockQty = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal deductQty = stockQty.min(remaining);
            BigDecimal beforeQuantity = stockQty;
            BigDecimal afterQuantity = stockQty.subtract(deductQty);
            stock.setStockQuantity(afterQuantity);
            stockMapper.updateById(stock);

            WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_MISC_OUT, TX_FLAG_OUT, deductQty,
                beforeQuantity, afterQuantity, now);
            tx.setSourceDocType(SOURCE_DOC_TYPE_MISC_ISSUE);
            tx.setSourceDocId(line.getIssueId());
            tx.setSourceDocLineId(line.getLineId());
            tx.setRemark("杂项出库");
            stockTxMapper.insert(tx);
            remaining = remaining.subtract(deductQty);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足");
        }
    }

    @Override
    public void postMiscRecpt(WmMiscRecptLine line, WmMiscRecpt recpt, Date now) {
        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("杂项入库数量必须大于0");
        }

        // 查找或创建库存记录
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        lqw.eq(recpt.getWarehouseId() != null, WmMaterialStock::getWarehouseId, recpt.getWarehouseId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        BigDecimal beforeQuantity = stock == null || stock.getStockQuantity() == null
            ? BigDecimal.ZERO : stock.getStockQuantity();
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
            stockMapper.insert(stock);
        } else {
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stockMapper.updateById(stock);
        }

        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_MISC_IN, TX_FLAG_IN, line.getQuantity(),
            beforeQuantity, afterQuantity, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_MISC_RECPT);
        tx.setSourceDocId(line.getRecptId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("杂项入库");
        stockTxMapper.insert(tx);
    }

    private List<WmMaterialStock> resolveMiscIssueStocks(WmMiscIssueLine line) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
        }
        return stockList;
    }

    private boolean hasPosted(String sourceDocType, Long sourceDocId) {
        Long count = stockTxMapper.selectCount(Wrappers.lambdaQuery(WmMaterialStockTx.class)
            .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
            .eq(WmMaterialStockTx::getSourceDocId, sourceDocId));
        return count != null && count > 0;
    }

    private WmMaterialStock findStockForReceive(WmMaterialReceiveDetail detail, WmMaterialReceive receive) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, detail.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(detail.getBaseUomId()), WmMaterialStock::getBaseUomId, detail.getBaseUomId());
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

    private List<WmMaterialStock> resolveSalesIssueStocks(WmSalesIssue salesIssue, WmSalesIssueLine line) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getWarehouseId, salesIssue.getWarehouseId());
        lqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
        lqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);

        List<WmMaterialStock> stockList = stockMapper.selectList(lqw);
        if (stockList == null || stockList.isEmpty()) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
        }
        return stockList;
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

    private WmMaterialStockTx buildSalesOutStockTx(WmSalesIssue salesIssue, WmSalesIssueLine line,
                                                   WmMaterialStock stock, BigDecimal beforeQuantity,
                                                   BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_SALES_OUT, TX_FLAG_OUT, line.getQuantityIssue(),
            beforeQuantity, afterQuantity, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_SALES_ISSUE);
        tx.setSourceDocId(salesIssue.getIssueId());
        tx.setSourceDocCode(salesIssue.getIssueCode());
        tx.setSourceDocLineId(line.getLineId());
        tx.setErpDate(salesIssue.getIssueDate());
        tx.setAttr1(salesIssue.getSoNo());
        tx.setAttr3(salesIssue.getSoId());
        tx.setRemark("销售出库扣减库存，业务单号：" + salesIssue.getIssueCode() + "，业务类型：" + SOURCE_DOC_TYPE_SALES_ISSUE);
        return tx;
    }

    private WmMaterialStockTx buildTransferOutTx(WmTransferLine line, WmMaterialStock stock,
                                                  BigDecimal txQuantity, BigDecimal beforeQuantity,
                                                  BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_TRANSFER_OUT, TX_FLAG_OUT, txQuantity,
            beforeQuantity, afterQuantity, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_TRANSFER);
        tx.setSourceDocId(line.getTransferId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("调拨出库，目标库位：" + line.getToLocationName());
        return tx;
    }

    private WmMaterialStockTx buildTransferInTx(WmTransferLine line, WmMaterialStock stock,
                                                 BigDecimal txQuantity, Date now) {
        BigDecimal beforeQty = stock.getStockQuantity().subtract(txQuantity);
        BigDecimal afterQty = stock.getStockQuantity();
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_TRANSFER_IN, TX_FLAG_IN, txQuantity,
            beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_TRANSFER);
        tx.setSourceDocId(line.getTransferId());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("调拨入库，来源库位：" + line.getFromLocationName());
        return tx;
    }

    private WmMaterialStockTx buildBaseTxFromStock(WmMaterialStock stock, String txType, Integer txFlag,
                                                   BigDecimal txQuantity, BigDecimal beforeQuantity,
                                                   BigDecimal afterQuantity, Date now) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType(txType);
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setMaterialSpec(stock.getMaterialSpec());
        tx.setBaseUomId(stock.getBaseUomId());
        tx.setBaseUomName(stock.getBaseUomName());
        tx.setBatchId(stock.getBatchId());
        tx.setBatchCode(stock.getBatchCode());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setAreaId(stock.getAreaId());
        tx.setAreaCode(stock.getAreaCode());
        tx.setAreaName(stock.getAreaName());
        tx.setLocationId(stock.getLocationId());
        tx.setLocationCode(stock.getLocationCode());
        tx.setLocationName(stock.getLocationName());
        tx.setMaterialStockId(stock.getStockId());
        tx.setTxFlag(txFlag);
        tx.setTxQuantity(txQuantity);
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        return tx;
    }
}
