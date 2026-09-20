package com.nexus.erp.prd.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * BOM 展开共用口径（ERP-PRD-01）。
 * <p>
 * 此前存在两条 BOM 展开口径不一致的入口：
 * <ul>
 *   <li>计划驱动（{@code PrdProductionPlanServiceImpl}）：含损耗率、CEILING 取整、需求日期按父层倒排（小时）</li>
 *   <li>手工「导入BOM明细」（{@code PrdMaterialRequirementServiceImpl#importBomDetails}）：
 *       不含损耗率、需求日期以「现在」为基准按 <b>天</b> 倒排</li>
 * </ul>
 * 本类抽出唯一实现，两条入口共用，避免同一 BOM 同一数量算出不同需求量与不同需求日期。
 *
 * @author nexus
 */
public final class BomExplosionCalculator {

    private BomExplosionCalculator() {
    }

    /**
     * 计算子项需求数量（含损耗率）。
     * <p>公式：{@code 父项数量 × 单位用量 × (1 + 损耗率/100)}；损耗率大于 0 时结果向上取整
     * （避免因损耗小数导致应领数量无法取件，如 10.5 件应取 11 件）。</p>
     *
     * @param parentQuantity   父项需求数量
     * @param quantityPerParent 单位父项用量
     * @param lossRate         损耗率（%，0~100）
     * @return 子项需求数量；父项数量或单位用量为空时返回 {@link BigDecimal#ZERO}
     */
    public static BigDecimal calculateChildQuantity(BigDecimal parentQuantity, BigDecimal quantityPerParent,
                                                    BigDecimal lossRate) {
        if (parentQuantity == null || quantityPerParent == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseQuantity = parentQuantity.multiply(quantityPerParent);
        if (lossRate != null && lossRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lossFactor = BigDecimal.ONE.add(
                lossRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            return baseQuantity.multiply(lossFactor).setScale(0, RoundingMode.CEILING);
        }
        return baseQuantity;
    }

    /**
     * 按提前期倒排需求日期。
     * <p>需求日期 = 父层需求日期 − 提前期（<b>小时</b>）。父层需求日期为空时以当前时间为基准
     * （顶层物料场景）；提前期为空或 ≤0 时返回基准日期本身。</p>
     *
     * @param parentRequiredDate 父层需求日期，可为 null
     * @param leadTimeHours      提前期（小时），接受任意数值类型（Integer/Long）
     * @return 需求日期
     */
    public static Date resolveRequiredDate(Date parentRequiredDate, Number leadTimeHours) {
        Date base = parentRequiredDate != null ? parentRequiredDate : new Date();
        if (leadTimeHours == null || leadTimeHours.longValue() <= 0) {
            return base;
        }
        return Date.from(base.toInstant().minusSeconds(leadTimeHours.longValue() * 3600L));
    }
}
