package com.nexus.erp.prd.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单行到货事实。
 *
 * @param purchaseLineId 采购订单行 ID
 * @param originalDate 原预计到货日
 * @param remainingQty 未到货数量
 */
public record PurchaseArrivalLine(Long purchaseLineId, LocalDate originalDate, BigDecimal remainingQty) {
}
