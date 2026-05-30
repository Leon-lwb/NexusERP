package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import com.nexus.erp.wm.domain.WmMiscIssueLine;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存过账服务实现
 * <p>
 * 采用策略模式委派各类库存操作到对应的策略实现，统一处理入库、出库、调拨、盘点等业务。
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStockOperationServiceImpl implements IWmStockOperationService {

    private final StockOperationStrategyFactory strategyFactory;

    /**
     * 物料入库过账
     *
     * @param receiveId 入库单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postMaterialReceive(Long receiveId) {
        strategyFactory.getStrategy(StockOperationTypeEnum.RECEIVE_IN).execute(receiveId);
    }

    /**
     * 销售出库过账
     *
     * @param issueId 销售出库单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postSalesIssue(Long issueId) {
        strategyFactory.getStrategy(StockOperationTypeEnum.SALES_OUT).execute(issueId);
    }

    /**
     * 库存调拨过账
     *
     * @param line 调拨单行对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postTransfer(WmTransferLine line) {
        strategyFactory.getStrategy(StockOperationTypeEnum.TRANSFER).execute(line);
    }

    /**
     * 盘点调整过账
     *
     * @param line          盘点单行对象
     * @param actualQuantity 实际盘点数量
     * @param bookQuantity  账面数量
     * @param diffQuantity  差异数量
     * @param now           操作时间
     */
    @Override
    public void postCountAdjust(WmStockTakingLine line, BigDecimal actualQuantity,
                                BigDecimal bookQuantity, BigDecimal diffQuantity, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.COUNT_ADJUST)
            .execute(line, actualQuantity, bookQuantity, diffQuantity, now);
    }

    /**
     * 杂项出库过账
     *
     * @param line 杂项出库单行对象
     * @param now  操作时间
     */
    @Override
    public void postMiscIssue(WmMiscIssueLine line, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.MISC_ISSUE).execute(line, now);
    }

    /**
     * 杂项入库过账
     *
     * @param line  杂项入库单行对象
     * @param recpt 杂项入库单对象
     * @param now   操作时间
     */
    @Override
    public void postMiscRecpt(WmMiscRecptLine line, WmMiscRecpt recpt, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.MISC_RECPT).execute(line, recpt, now);
    }

    /**
     * 生产领料过账
     *
     * @param line 生产领料单行对象
     * @param now  操作时间
     */
    @Override
    public void postProductionIssue(WmProductionIssueLine line, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.PRODUCTION_ISSUE).execute(line, now);
    }

    /**
     * 生产完工入库过账
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postProductionCompletion(Long woId, Long materialId, String materialCode, String materialName,
                                          BigDecimal quantity, Long warehouseId, Long locationId, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.PRODUCTION_IN)
            .execute(woId, materialId, materialCode, materialName, quantity, warehouseId, locationId, now);
    }

    /**
     * 返工出库过账
     * <p>
     * 通过策略工厂获取 REWORK_ISSUE 策略执行返工出库操作。
     *
     * @param reworkId     返工订单ID
     * @param materialId   物料ID
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param quantity     出库数量
     * @param warehouseId  仓库ID
     * @param locationId   库位ID
     * @param now          操作时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postReworkIssue(Long reworkId, Long materialId, String materialCode, String materialName, BigDecimal quantity, Long warehouseId, Long locationId, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.REWORK_ISSUE)
            .execute(reworkId, materialId, materialCode, materialName, quantity, warehouseId, locationId, now);
    }

    /**
     * 返工入库过账
     * <p>
     * 通过策略工厂获取 REWORK_RECEIVE 策略执行返工入库操作。
     *
     * @param reworkId     返工订单ID
     * @param materialId   物料ID
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param quantity     入库数量
     * @param warehouseId  仓库ID
     * @param locationId   库位ID
     * @param now          操作时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postReworkReceive(Long reworkId, Long materialId, String materialCode, String materialName, BigDecimal quantity, Long warehouseId, Long locationId, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.REWORK_RECEIVE)
            .execute(reworkId, materialId, materialCode, materialName, quantity, warehouseId, locationId, now);
    }
}
