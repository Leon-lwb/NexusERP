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
 * 库存过账服务实现 — 委派策略模式
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmStockOperationServiceImpl implements IWmStockOperationService {

    private final StockOperationStrategyFactory strategyFactory;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postMaterialReceive(Long receiveId) {
        strategyFactory.getStrategy(StockOperationTypeEnum.RECEIVE_IN).execute(receiveId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postSalesIssue(Long issueId) {
        strategyFactory.getStrategy(StockOperationTypeEnum.SALES_OUT).execute(issueId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postTransfer(WmTransferLine line) {
        strategyFactory.getStrategy(StockOperationTypeEnum.TRANSFER).execute(line);
    }

    @Override
    public void postCountAdjust(WmStockTakingLine line, BigDecimal actualQuantity,
                                BigDecimal bookQuantity, BigDecimal diffQuantity, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.COUNT_ADJUST)
            .execute(line, actualQuantity, bookQuantity, diffQuantity, now);
    }

    @Override
    public void postMiscIssue(WmMiscIssueLine line, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.MISC_ISSUE).execute(line, now);
    }

    @Override
    public void postMiscRecpt(WmMiscRecptLine line, WmMiscRecpt recpt, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.MISC_RECPT).execute(line, recpt, now);
    }

    @Override
    public void postProductionIssue(WmProductionIssueLine line, Date now) {
        strategyFactory.getStrategy(StockOperationTypeEnum.PRODUCTION_ISSUE).execute(line, now);
    }
}
