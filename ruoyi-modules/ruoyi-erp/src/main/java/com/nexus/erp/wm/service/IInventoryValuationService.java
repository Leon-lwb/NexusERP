package com.nexus.erp.wm.service;

import java.math.BigDecimal;

public interface IInventoryValuationService {

    BigDecimal calculateMovingAverage(Long stockId, BigDecimal inQty, BigDecimal inUnitCost);

    void updateStockCost(Long stockId, BigDecimal inQty, BigDecimal inUnitCost);
}
