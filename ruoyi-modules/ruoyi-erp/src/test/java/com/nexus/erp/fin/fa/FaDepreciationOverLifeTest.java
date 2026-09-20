package com.nexus.erp.fin.fa;

import com.nexus.erp.fin.fa.constant.FaConstants;
import com.nexus.erp.fin.fa.domain.FaAsset;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import com.nexus.erp.fin.fa.service.impl.FaDepreciationServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 折旧计算边界单元测试（收尾批次：FIN-FA-04）。
 * <p>核心断言：年数总和法在超过使用年限的资产上不得产生<b>负折旧额</b>
 * （修复前 {@code remaining = life - (monthsInService - 1)} 为负 → 金额为负 → 冲回已提折旧）。</p>
 * <p>被测方法 {@code calcMonthlyDepreciation} 不触碰任何注入依赖，故以 null 依赖直接构造。</p>
 */
@Tag("dev")
@Tag("local")
class FaDepreciationOverLifeTest {

    private final FaDepreciationServiceImpl service =
        new FaDepreciationServiceImpl(null, null, null, null, null, null);

    private FaAsset asset(String method, String original, String cumulative, String salvage, Integer life) {
        FaAsset a = new FaAsset();
        a.setDepreciationMethod(method);
        a.setOriginalValue(new BigDecimal(original));
        a.setCumulativeDepreciation(new BigDecimal(cumulative));
        a.setSalvageValue(new BigDecimal(salvage));
        a.setUsefulLife(life);
        return a;
    }

    private FaAssetCategory category(String method, Integer life) {
        FaAssetCategory c = new FaAssetCategory();
        c.setDepreciationMethod(method);
        c.setUsefulLife(life);
        return c;
    }

    /** 年数总和法：使用年限 5 年、已用 6 年（超年限）→ 计提 0，不得为负。 */
    @Test
    void sumOfYearsReturnsZeroBeyondUsefulLife() {
        FaAsset a = asset(FaConstants.METHOD_SUM_OF_YEARS, "100000", "90000", "0", 5);

        BigDecimal amount = service.calcMonthlyDepreciation(a, category(null, 5), 6L);

        assertEquals(0, amount.compareTo(BigDecimal.ZERO),
            "超使用年限不得计提负折旧, 实际: " + amount);
    }

    /** 年数总和法：超年限且残值仍大于净值时同样为 0，不出现负数。 */
    @Test
    void sumOfYearsNeverNegativeForAnyMonthsInService() {
        FaAsset a = asset(FaConstants.METHOD_SUM_OF_YEARS, "100000", "60000", "50000", 5);

        for (long months = 1; months <= 80; months++) {
            BigDecimal amount = service.calcMonthlyDepreciation(a, category(null, 5), months);
            assertTrue(amount.signum() >= 0,
                "第 " + months + " 月计提额不得为负, 实际: " + amount);
        }
    }

    /** 回归：正常年限内的年数总和法仍按 (原值-残值) × 剩余月/月总和 计提。 */
    @Test
    void sumOfYearsStillComputesWithinUsefulLife() {
        // life=5, sumOfDigits=15, monthsInService=1 → remaining=5, (100000-0)*5/15 = 33333.33
        FaAsset a = asset(FaConstants.METHOD_SUM_OF_YEARS, "100000", "0", "0", 5);

        BigDecimal amount = service.calcMonthlyDepreciation(a, category(null, 5), 1L);

        assertEquals(0, amount.compareTo(new BigDecimal("33333.33")), "实际: " + amount);
    }
}
