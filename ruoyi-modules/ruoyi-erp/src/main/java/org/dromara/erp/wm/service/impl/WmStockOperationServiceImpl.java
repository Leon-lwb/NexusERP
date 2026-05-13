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
import org.dromara.erp.wm.domain.WmSalesIssue;
import org.dromara.erp.wm.domain.WmSalesIssueLine;
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
            WmMaterialStock stock = resolveSalesIssueStock(salesIssue, line);
            BigDecimal beforeQuantity = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
            if (beforeQuantity.compareTo(line.getQuantityIssue()) < 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足，当前库存："
                    + beforeQuantity + "，出库数量：" + line.getQuantityIssue());
            }

            BigDecimal afterQuantity = beforeQuantity.subtract(line.getQuantityIssue());
            stock.setStockQuantity(afterQuantity);
            stockMapper.updateById(stock);
            stockTxMapper.insert(buildSalesOutStockTx(salesIssue, line, stock, beforeQuantity, afterQuantity, now));
        }
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

    private WmMaterialStock resolveSalesIssueStock(WmSalesIssue salesIssue, WmSalesIssueLine line) {
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
        if (stockList.size() > 1) {
            throw new RuntimeException("物料【" + line.getMaterialName() + "】存在多条库存记录，请选择具体库存记录或补全批次/库位后出库");
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
