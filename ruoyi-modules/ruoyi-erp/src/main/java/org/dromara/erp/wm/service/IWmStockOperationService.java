package org.dromara.erp.wm.service;

import org.dromara.erp.wm.domain.WmMiscIssueLine;
import org.dromara.erp.wm.domain.WmMiscRecpt;
import org.dromara.erp.wm.domain.WmMiscRecptLine;
import org.dromara.erp.wm.domain.WmStockTakingLine;
import org.dromara.erp.wm.domain.WmTransferLine;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存过账服务
 *
 * @author ruoyi
 */
public interface IWmStockOperationService {

    /**
     * 物料入库单过账入库存并生成流水
     */
    void postMaterialReceive(Long receiveId);

    /**
     * 销售出库单过账扣库存并生成流水
     */
    void postSalesIssue(Long issueId);

    /**
     * 调拨行过账（来源减 → 目标增）
     */
    void postTransfer(WmTransferLine line);

    /**
     * 盘点差异过账（盘盈/盘亏调整库存）
     */
    void postCountAdjust(WmStockTakingLine line, BigDecimal actualQuantity,
                         BigDecimal bookQuantity, BigDecimal diffQuantity, Date now);

    /**
     * 杂项出库过账（扣减库存）
     */
    void postMiscIssue(WmMiscIssueLine line, Date now);

    /**
     * 杂项入库过账（增加库存）
     */
    void postMiscRecpt(WmMiscRecptLine line, WmMiscRecpt recpt, Date now);
}
