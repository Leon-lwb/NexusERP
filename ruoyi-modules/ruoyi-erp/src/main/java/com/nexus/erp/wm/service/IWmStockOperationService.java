package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.WmMiscIssueLine;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmTransferLine;

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

    /**
     * 生产领料过账（FIFO扣减库存）
     */
    void postProductionIssue(WmProductionIssueLine line, Date now);

    /**
     * 完工入库：工单完工时将成品/半成品入库
     *
     * @param woId         工单ID
     * @param materialId   物料ID
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param quantity     入库数量
     * @param warehouseId  仓库ID
     * @param locationId   库位ID
     * @param now          操作时间
     */
    void postProductionCompletion(Long woId, Long materialId, String materialCode, String materialName,
                                   BigDecimal quantity, Long warehouseId, Long locationId, Date now);
}
