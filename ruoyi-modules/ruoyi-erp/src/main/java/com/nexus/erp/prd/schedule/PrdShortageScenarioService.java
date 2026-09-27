package com.nexus.erp.prd.schedule;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缺料场景推演服务。
 * <p>
 * 仅负责复制输入、按采购行事实重建场景到货并调用纯推演器，不访问缓存或持久化设施。
 * </p>
 */
@Component
public class PrdShortageScenarioService {

    /**
     * 推演基础场景或采购行到货日期覆盖场景。
     *
     * @param base 基础缺料输入
     * @param purchaseLines 采购订单行到货事实；为空时沿用基础输入中的聚合到货
     * @param arrivalOverrides 采购订单行 ID 到新到货日的覆盖
     * @param today 推演基准日
     * @return 缺料推演结果
     */
    public PrdShortageResult simulate(PrdShortageInput base,
                                      List<PurchaseArrivalLine> purchaseLines,
                                      Map<Long, LocalDate> arrivalOverrides,
                                      LocalDate today) {
        requireArgument(base != null, "base 不能为空");
        requireArgument(purchaseLines != null, "purchaseLines 不能为空");
        requireArgument(arrivalOverrides != null, "arrivalOverrides 不能为空");
        requireArgument(today != null, "today 不能为空");

        if (purchaseLines.isEmpty() && !arrivalOverrides.isEmpty()) {
            throw new IllegalArgumentException("采购行到货覆盖缺少原始采购行数据");
        }

        PrdShortageInput copy = copyStockAndDemands(base);
        if (purchaseLines.isEmpty()) {
            copy.getArrivals().putAll(base.getArrivals());
        } else {
            rebuildArrivals(copy, purchaseLines, arrivalOverrides);
        }
        return PrdShortageSimulator.simulate(copy, today);
    }

    private PrdShortageInput copyStockAndDemands(PrdShortageInput base) {
        PrdShortageInput copy = new PrdShortageInput();
        copy.setMaterialId(base.getMaterialId());
        copy.setMaterialCode(base.getMaterialCode());
        copy.setMaterialName(base.getMaterialName());
        copy.setStockQty(base.getStockQty());
        copy.setSafetyStock(base.getSafetyStock());
        copy.getDemands().putAll(base.getDemands());
        return copy;
    }

    private void rebuildArrivals(PrdShortageInput copy,
                                 List<PurchaseArrivalLine> purchaseLines,
                                 Map<Long, LocalDate> arrivalOverrides) {
        Set<Long> purchaseLineIds = validatePurchaseLines(purchaseLines);
        for (Map.Entry<Long, LocalDate> override : arrivalOverrides.entrySet()) {
            if (!purchaseLineIds.contains(override.getKey())) {
                throw new IllegalArgumentException("到货覆盖包含未知采购行 ID: " + override.getKey());
            }
            if (override.getValue() == null) {
                throw new IllegalArgumentException("采购行 " + override.getKey() + " 的覆盖到货日不能为空");
            }
        }

        for (PurchaseArrivalLine line : purchaseLines) {
            LocalDate arrivalDate = arrivalOverrides.getOrDefault(line.purchaseLineId(), line.originalDate());
            copy.addArrival(arrivalDate, line.remainingQty());
        }
    }

    private Set<Long> validatePurchaseLines(List<PurchaseArrivalLine> purchaseLines) {
        Set<Long> purchaseLineIds = new HashSet<>();
        for (PurchaseArrivalLine line : purchaseLines) {
            if (line == null) {
                throw new IllegalArgumentException("采购行数据不能为空");
            }
            if (line.purchaseLineId() == null) {
                throw new IllegalArgumentException("采购行 ID 不能为空");
            }
            if (!purchaseLineIds.add(line.purchaseLineId())) {
                throw new IllegalArgumentException("采购行 ID 重复: " + line.purchaseLineId());
            }
            if (line.originalDate() == null) {
                throw new IllegalArgumentException("采购行 " + line.purchaseLineId() + " 的原始到货日不能为空");
            }
            if (line.remainingQty() == null) {
                throw new IllegalArgumentException("采购行 " + line.purchaseLineId() + " 的剩余数量不能为空");
            }
            if (line.remainingQty().signum() <= 0) {
                throw new IllegalArgumentException("采购行 " + line.purchaseLineId() + " 的剩余数量必须为正数");
            }
        }
        return purchaseLineIds;
    }

    private void requireArgument(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
