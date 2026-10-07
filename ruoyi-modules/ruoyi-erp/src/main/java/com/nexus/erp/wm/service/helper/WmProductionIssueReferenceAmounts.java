package com.nexus.erp.wm.service.helper;

import com.nexus.erp.wm.domain.WmProductionIssueLine;

import java.math.BigDecimal;
import java.util.List;

/** 生产领料参考金额口径；不读取库存成本，不追认历史参考价格。 */
public final class WmProductionIssueReferenceAmounts {

    private WmProductionIssueReferenceAmounts() {
    }

    /** 已知明细金额优先，否则仅在参考单价和领料数量均已知时计算。 */
    public static BigDecimal lineAmount(WmProductionIssueLine line) {
        if (line == null) {
            return null;
        }
        BigDecimal quantity = line.getCurrentQty() != null && line.getCurrentQty().signum() > 0
            ? line.getCurrentQty() : line.getIssuedQty();

        if (line.getAmount() != null) {
            return line.getAmount();
        }
        return line.getUnitPrice() == null || quantity == null ? null : quantity.multiply(line.getUnitPrice());
    }

    /** 任一明细金额未知时总额未知，不能跳过未知项后宣称完整参考总价。 */
    public static BigDecimal totalAmount(List<WmProductionIssueLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (WmProductionIssueLine line : lines) {
            BigDecimal amount = lineAmount(line);
            if (amount == null) {
                return null;
            }
            total = total.add(amount);
        }
        return total;
    }
}
