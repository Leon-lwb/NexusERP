package com.nexus.erp.prd.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 供应链缺料推演引擎（纯函数, 可单测）
 * <p>
 * 逐日推演: 余额 = 库存 + Σ(截至当日到货) − Σ(截至当日需求) − 安全库存。
 * 首次余额 &lt; 0 的日期 = 最早缺料日, 最深负值 = 最大缺口。
 * 建议动作: 在途可覆盖但到货晚于需求日 → 催货; 无在途/在途不足 → 提前采购;
 * 仅安全库存线附近波动 → 调整安全库存。
 * </p>
 */
public final class PrdShortageSimulator {

    /** 建议动作: 催货 */
    public static final String SUGGEST_URGE = "URGE";
    /** 建议动作: 提前采购 */
    public static final String SUGGEST_PURCHASE = "PURCHASE";
    /** 建议动作: 调整安全库存 */
    public static final String SUGGEST_ADJUST_SAFETY = "ADJUST_SAFETY";

    private PrdShortageSimulator() {
    }

    /**
     * 单物料逐日推演
     *
     * @param input 物料聚合输入(需求/在途/库存/安全库存)
     * @param today 推演基准日
     * @return 推演结果(无缺口时 earliestShortageDate 为 null)
     */
    public static PrdShortageResult simulate(PrdShortageInput input, LocalDate today) {
        PrdShortageResult result = new PrdShortageResult();
        result.setMaterialId(input.getMaterialId());
        result.setMaterialCode(input.getMaterialCode());
        result.setMaterialName(input.getMaterialName());
        result.setStockQty(input.getStockQty());
        result.setSafetyStock(input.getSafetyStock());
        result.setTotalDemand(input.totalDemand());
        result.setOnOrderQty(input.totalOnOrder());

        // 推演时间轴: 需求与到货日期的并集
        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.addAll(input.getDemands().keySet());
        dates.addAll(input.getArrivals().keySet());
        if (dates.isEmpty()) {
            result.setSuggestionText("无需求计划, 无缺料风险");
            return result;
        }

        BigDecimal balance = input.getStockQty().subtract(input.getSafetyStock());
        LocalDate earliestShortage = null;
        BigDecimal maxGap = BigDecimal.ZERO;
        long gapDays = 0;
        List<LocalDate> shortageDemandDates = new ArrayList<>();

        for (LocalDate date : dates) {
            // 截至当日的累计到货与累计需求
            BigDecimal cumulativeArrival = sumUpTo(input.getArrivals(), date);
            BigDecimal cumulativeDemand = sumUpTo(input.getDemands(), date);
            // 余额 = 初始可用 + 累计到货 − 累计需求
            BigDecimal current = input.getStockQty().subtract(input.getSafetyStock())
                .add(cumulativeArrival).subtract(cumulativeDemand);
            if (current.signum() < 0) {
                if (earliestShortage == null) {
                    earliestShortage = date;
                }
                gapDays++;
                if (input.getDemands().containsKey(date)) shortageDemandDates.add(date);
                BigDecimal gap = current.abs();
                if (gap.compareTo(maxGap) > 0) {
                    maxGap = gap;
                }
            }
            // 保留最后一次推演值供建议判断
            balance = current;
        }

        result.setEarliestShortageDate(earliestShortage);
        result.setMaxGap(maxGap);
        result.setGapDays(gapDays);
        result.setShortageDemandDates(shortageDemandDates);

        if (earliestShortage == null) {
            // 无缺口: 最低余额是否逼近安全线(0)
            if (balance.signum() == 0) {
                result.setSuggestion(SUGGEST_ADJUST_SAFETY);
                result.setSuggestionText("恰好踩到安全库存线, 建议复核安全库存设置或补充少量采购");
            } else {
                result.setSuggestionText("推演期内无缺料风险");
            }
        } else {
            applySuggestion(result, input, earliestShortage);
        }
        return result;
    }

    /**
     * 建议动作: 在途量能覆盖总缺口 → 催货(把到货提前到首个缺料日之前);
     * 否则 → 提前采购(缺口 = 在途覆盖不了的净额)。
     */
    private static void applySuggestion(PrdShortageResult result, PrdShortageInput input, LocalDate earliestShortage) {
        BigDecimal netGap = result.getTotalDemand()
            .subtract(input.getStockQty().subtract(input.getSafetyStock()))
            .subtract(result.getOnOrderQty());
        if (result.getOnOrderQty().signum() > 0 && netGap.signum() <= 0) {
            result.setSuggestion(SUGGEST_URGE);
            // 找出首个晚于最早缺料日的在途批次
            LocalDate lateArrival = input.getArrivals().ceilingKey(earliestShortage);
            result.setSuggestionText(String.format(
                "在途采购可覆盖缺口, 但到货晚于需求: 建议催货将 %s 前的在途批次提前到 %s 之前",
                lateArrival, earliestShortage));
        } else {
            result.setSuggestion(SUGGEST_PURCHASE);
            result.setSuggestionText(String.format(
                "在途不足以覆盖缺口, 建议按净缺口 %.4f 提前下达采购(最早缺料日 %s)",
                netGap.max(BigDecimal.ZERO), earliestShortage));
        }
    }

    /**
     * 汇总 map 中 ≤ date 的全部数值
     */
    private static BigDecimal sumUpTo(TreeMap<LocalDate, BigDecimal> map, LocalDate date) {
        return map.headMap(date, true).values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 批量推演并按最早缺料日升序返回(无风险的排最后)
     */
    public static List<PrdShortageResult> simulateAll(List<PrdShortageInput> inputs, LocalDate today) {
        List<PrdShortageResult> results = new ArrayList<>();
        for (PrdShortageInput input : inputs) {
            results.add(simulate(input, today));
        }
        results.sort((a, b) -> {
            if (a.getEarliestShortageDate() == null && b.getEarliestShortageDate() == null) {
                return 0;
            }
            if (a.getEarliestShortageDate() == null) {
                return 1;
            }
            if (b.getEarliestShortageDate() == null) {
                return -1;
            }
            return a.getEarliestShortageDate().compareTo(b.getEarliestShortageDate());
        });
        return results;
    }
}
