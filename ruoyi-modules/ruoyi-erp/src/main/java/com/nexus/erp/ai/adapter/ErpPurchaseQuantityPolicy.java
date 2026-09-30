package com.nexus.erp.ai.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 采用校验失败即拒绝策略的纯采购数量与 UOM 校验，不执行 I/O、锁定、授权或预占。
 * verified 标志与版本是 ERP 调用方提供的声明，并非数据库证据：
 * 调用方使用允许结果创建草稿前，必须在 ERP 事务内重新读取同租户的来源、条款、
 * UOM 版本以及可用分配量。
 */
public final class ErpPurchaseQuantityPolicy {
    private static final BigDecimal ONE = BigDecimal.ONE;

    public enum RejectionReason {
        UNVERIFIED_SOURCE, UNVERIFIED_TERMS, UNVERIFIED_UNIT, SCOPE_MISMATCH, UNIT_MISMATCH,
        TERMS_NOT_EFFECTIVE, INVALID_QUANTITY, QUANTITY_OUT_OF_RANGE, INVALID_PRECISION,
        NON_EXACT_CONVERSION, BELOW_MOQ, NOT_ORDER_MULTIPLE, PACK_SPLIT_NOT_ALLOWED,
        EXCESS_BASE, INSUFFICIENT_BASE
    }

    /** 调用方认证的、分配给此物料 MRP 来源的数量，以物料基本单位计量。 */
    public record Demand(String tenantId, String materialId, String baseUomId, BigDecimal coveredDemandBase,
                         LocalDate requiredDate, Instant snapshotAt, boolean verified) { }

    /** 经独立核验的物料主数据事实；单凭 MRP.unit 不能证明其基本单位。 */
    public record Material(String tenantId, String materialId, String baseUomId, boolean verified) { }

    /** NULL 字段表示未知，绝不从历史 min_order_qty 或单位名称继承取值。 */
    public record SupplierTerms(String tenantId, String materialId, String termsId, String orderUomId,
                                String moqUomId, BigDecimal verifiedMoqQty, BigDecimal orderMultiple,
                                Boolean allowSplit, BigDecimal unitsPerPack, String termsVersion,
                                Instant effectiveAt, Instant expiresAt, Instant verifiedAt, boolean verified) { }

    /** basePerUnit 是调用方核验的换算量，表示一个此类单位包含的物料基本单位数量。 */
    public record TrustedUnit(String tenantId, String unitId, String baseUnitId, String dimensionId,
                              BigDecimal basePerUnit, String conversionVersion, Integer quantityScale,
                              Instant verifiedAt, boolean verified) { }

    /** 传入的候选数量必须由 ERP 独立核验；null 表示仅允许精确相除。 */
    public record Input(Demand demand, Material material, SupplierTerms terms, TrustedUnit baseUnit,
                        TrustedUnit orderUnit, TrustedUnit moqUnit, BigDecimal candidateOrderQty,
                        boolean candidateVerified) { }

    /** 成功或计算出数量不匹配时填充数量；其他拒绝情形的数量为 null。 */
    public record Evaluation(boolean allowed, RejectionReason reason, BigDecimal requestedOrderQty,
                             BigDecimal orderedBaseQty, BigDecimal excessBase) { }

    public Evaluation evaluate(Input input) {
        if (input == null || input.demand() == null) {
            return reject(RejectionReason.UNVERIFIED_SOURCE);
        }
        Demand d = input.demand();
        if (!d.verified() || !id(d.tenantId()) || !id(d.materialId()) || !id(d.baseUomId())
            || d.requiredDate() == null || d.snapshotAt() == null || d.coveredDemandBase() == null
            || (input.candidateOrderQty() != null && !input.candidateVerified())) {
            return reject(RejectionReason.UNVERIFIED_SOURCE);
        }
        if (d.coveredDemandBase().signum() <= 0) {
            return reject(RejectionReason.INVALID_QUANTITY);
        }
        if (!decimal(d.coveredDemandBase(), 4)) {
            return reject(RejectionReason.QUANTITY_OUT_OF_RANGE);
        }

        Material material = input.material();
        if (material == null || !material.verified() || !id(material.tenantId())
            || !id(material.materialId()) || !id(material.baseUomId())) {
            return reject(RejectionReason.UNVERIFIED_SOURCE);
        }
        if (!d.tenantId().equals(material.tenantId()) || !d.materialId().equals(material.materialId())) {
            return reject(RejectionReason.SCOPE_MISMATCH);
        }
        if (!d.baseUomId().equals(material.baseUomId())) {
            return reject(RejectionReason.UNIT_MISMATCH);
        }

        SupplierTerms t = input.terms();
        if (t == null || !t.verified() || !id(t.termsId()) || !id(t.orderUomId()) || !id(t.moqUomId())
            || blank(t.termsVersion()) || t.verifiedMoqQty() == null || t.orderMultiple() == null
            || t.allowSplit() == null || t.unitsPerPack() == null || t.effectiveAt() == null
            || t.expiresAt() == null || t.verifiedAt() == null) {
            return reject(RejectionReason.UNVERIFIED_TERMS);
        }
        if (!d.tenantId().equals(t.tenantId()) || !d.materialId().equals(t.materialId())) {
            return reject(RejectionReason.SCOPE_MISMATCH);
        }
        if (t.effectiveAt().isAfter(t.expiresAt()) || t.effectiveAt().isAfter(d.snapshotAt())
            || t.expiresAt().isBefore(d.snapshotAt()) || t.verifiedAt().isAfter(d.snapshotAt())) {
            return reject(RejectionReason.TERMS_NOT_EFFECTIVE);
        }
        if (t.verifiedMoqQty().signum() <= 0 || t.orderMultiple().signum() <= 0
            || t.unitsPerPack().signum() <= 0) {
            return reject(RejectionReason.INVALID_QUANTITY);
        }
        if (!decimal(t.verifiedMoqQty(), 4) || !decimal(t.orderMultiple(), 4)
            || !decimal(t.unitsPerPack(), 4)) {
            return reject(RejectionReason.QUANTITY_OUT_OF_RANGE);
        }
        if (!atScale(t.verifiedMoqQty(), 4) || !atScale(t.orderMultiple(), 4)
            || !atScale(t.unitsPerPack(), 4)) {
            return reject(RejectionReason.INVALID_PRECISION);
        }

        TrustedUnit base = input.baseUnit();
        TrustedUnit order = input.orderUnit();
        TrustedUnit moq = input.moqUnit();
        if (!trusted(base, d.snapshotAt()) || !trusted(order, d.snapshotAt())
            || !trusted(moq, d.snapshotAt())) {
            return reject(RejectionReason.UNVERIFIED_UNIT);
        }
        if (!d.tenantId().equals(base.tenantId()) || !d.tenantId().equals(order.tenantId())
            || !d.tenantId().equals(moq.tenantId())) {
            return reject(RejectionReason.SCOPE_MISMATCH);
        }
        if (!base.unitId().equals(d.baseUomId()) || !base.baseUnitId().equals(d.baseUomId())
            || !order.baseUnitId().equals(d.baseUomId()) || !moq.baseUnitId().equals(d.baseUomId())
            || !order.unitId().equals(t.orderUomId()) || !moq.unitId().equals(t.moqUomId())
            || !base.dimensionId().equals(order.dimensionId())
            || !base.dimensionId().equals(moq.dimensionId())
            || base.basePerUnit().compareTo(ONE) != 0
            || (base.unitId().equals(order.unitId()) && !base.equals(order))
            || (base.unitId().equals(moq.unitId()) && !base.equals(moq))
            || (order.unitId().equals(moq.unitId()) && !order.equals(moq))) {
            return reject(RejectionReason.UNIT_MISMATCH);
        }
        if (base.basePerUnit().signum() <= 0 || order.basePerUnit().signum() <= 0
            || moq.basePerUnit().signum() <= 0) {
            return reject(RejectionReason.INVALID_QUANTITY);
        }
        if (!decimal(base.basePerUnit(), 6) || !decimal(order.basePerUnit(), 6)
            || !decimal(moq.basePerUnit(), 6)) {
            return reject(RejectionReason.QUANTITY_OUT_OF_RANGE);
        }
        if (!atScale(base.basePerUnit(), 6) || !atScale(order.basePerUnit(), 6)
            || !atScale(moq.basePerUnit(), 6)) {
            return reject(RejectionReason.INVALID_PRECISION);
        }
        if (!atScale(d.coveredDemandBase(), 4) || !atScale(d.coveredDemandBase(), base.quantityScale())
            || !atScale(t.verifiedMoqQty(), moq.quantityScale())
            || !atScale(t.orderMultiple(), order.quantityScale())
            || !atScale(t.unitsPerPack(), base.quantityScale())) {
            return reject(RejectionReason.INVALID_PRECISION);
        }

        BigDecimal requested;
        if (input.candidateOrderQty() == null) {
            /** 禁止舍入、按 MOQ 向上调整或凭空构造包装数量。 */
            try {
                requested = d.coveredDemandBase().divide(order.basePerUnit());
            } catch (ArithmeticException nonTerminating) {
                return reject(RejectionReason.NON_EXACT_CONVERSION);
            }
        } else {
            requested = input.candidateOrderQty();
        }
        if (requested.signum() <= 0) {
            return reject(RejectionReason.INVALID_QUANTITY);
        }
        if (!decimal(requested, 4)) {
            return reject(RejectionReason.QUANTITY_OUT_OF_RANGE);
        }
        if (!atScale(requested, 4) || !atScale(requested, order.quantityScale())) {
            return reject(RejectionReason.INVALID_PRECISION);
        }
        BigDecimal orderedBase = requested.multiply(order.basePerUnit());
        BigDecimal moqBase = t.verifiedMoqQty().multiply(moq.basePerUnit());
        if (!decimal(orderedBase, 4) || !decimal(moqBase, 4)) {
            return reject(RejectionReason.QUANTITY_OUT_OF_RANGE);
        }
        if (!atScale(orderedBase, 4) || !atScale(moqBase, 4)
            || !atScale(orderedBase, base.quantityScale()) || !atScale(moqBase, base.quantityScale())) {
            return reject(RejectionReason.INVALID_PRECISION);
        }
        BigDecimal excess = orderedBase.subtract(d.coveredDemandBase());
        if (orderedBase.compareTo(moqBase) < 0) {
            return reject(RejectionReason.BELOW_MOQ, requested, orderedBase, excess);
        }
        if (requested.remainder(t.orderMultiple()).signum() != 0) {
            return reject(RejectionReason.NOT_ORDER_MULTIPLE, requested, orderedBase, excess);
        }
        if (!t.allowSplit() && orderedBase.remainder(t.unitsPerPack()).signum() != 0) {
            return reject(RejectionReason.PACK_SPLIT_NOT_ALLOWED, requested, orderedBase, excess);
        }
        if (excess.signum() > 0) {
            return reject(RejectionReason.EXCESS_BASE, requested, orderedBase, excess);
        }
        if (excess.signum() < 0) {
            return reject(RejectionReason.INSUFFICIENT_BASE, requested, orderedBase, excess);
        }
        return new Evaluation(true, null, requested, orderedBase, excess);
    }

    private static boolean trusted(TrustedUnit u, Instant snapshot) {
        return u != null && u.verified() && id(u.tenantId()) && id(u.unitId()) && id(u.baseUnitId())
            && !blank(u.dimensionId()) && !blank(u.conversionVersion()) && u.basePerUnit() != null
            && u.quantityScale() != null && u.quantityScale() >= 0 && u.quantityScale() <= 4
            && u.verifiedAt() != null && !u.verifiedAt().isAfter(snapshot);
    }

    /**
     * ERP 数量与条款采用 DECIMAL(18,4)，存储的 UOM 换算率采用 DECIMAL(18,6)。
     * 数值大小和小数精度分别对应不同拒绝原因；两者都不得舍入。
     */
    private static boolean decimal(BigDecimal n, int scale) {
        BigDecimal normalized = n.stripTrailingZeros();
        return (long) normalized.precision() - normalized.scale() <= 18L - scale;
    }

    private static boolean atScale(BigDecimal n, int scale) {
        return n.stripTrailingZeros().scale() <= scale;
    }

    private static boolean id(String s) {
        if (blank(s)) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Evaluation reject(RejectionReason reason) {
        return reject(reason, null, null, null);
    }

    private static Evaluation reject(RejectionReason reason, BigDecimal requested, BigDecimal ordered,
                                     BigDecimal excess) {
        return new Evaluation(false, reason, requested, ordered, excess);
    }
}
