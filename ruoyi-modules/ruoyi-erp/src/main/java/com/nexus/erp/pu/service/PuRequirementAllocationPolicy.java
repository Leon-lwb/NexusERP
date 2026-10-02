package com.nexus.erp.pu.service;

import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 维护 MRP 申请意向局部账本不变量的纯策略，适用范围有界。
 *
 * <p>有效结果并不能证明 MRP 来源、基本 UOM、真实订单行、全局可申请余额、
 * 不存在其他下游事实或已获授权。调用方必须在 ERP 物料 guard 和资源锁保护下
 * 独立核验这些事实，再于同一 ERP 事务中使用现有版本 CAS 持久化账本及其只追加事件。
 * 此类不执行 I/O，也不开放任何业务路径。</p>
 *
 * <p>仅原始且未转单的 REQUEST_INTENT 可由申请释放。新增订单履约/结案/退货策略
 * 仅维护局部四量守恒，不认证真实单据、库存入账或准时供给。已转移或已释放的快照不能
 * 重新用作新意向；持久化键的唯一性与重放核对仍由调用方负责。</p>
 */
public final class PuRequirementAllocationPolicy {
    private static final String MRP_DETAIL = "MRP_DETAIL";
    private static final String REQUEST_INTENT = "REQUEST_INTENT";
    private static final String ORDER_COMMITTED = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    private static final String RELEASED = "RELEASED";
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final int QUANTITY_SCALE = 4;
    private static final int QUANTITY_INTEGER_DIGITS = 14;

    private PuRequirementAllocationPolicy() {
    }

    /**
     * 从明确归属且覆盖量为正的新种子创建意向。
     * 可选的初始状态、余额和版本必须已符合新意向要求；
     * 绝不抹除已有订单、时间戳或释放事实来重新创建一行。
     *
     * @param ledger 带有明确归属和覆盖量的新账本种子
     * @param now 本次变更时间
     * @return 供调用方在受保护的同一 ERP 事务中持久化的变更快照
     */
    public static Change createRequestIntent(PuRequirementAllocationLedger ledger, Date now) {
        Date timestamp = requireNow(now);
        PuRequirementAllocationLedger row = checkedCopy(ledger);
        requireUncommitted(row);
        if (row.getCreateTime() != null || row.getUpdateTime() != null
            || (row.getAllocationStatus() != null && !REQUEST_INTENT.equals(row.getAllocationStatus()))
            || (row.getVersion() != null && row.getVersion() != 0)) {
            throw new IllegalArgumentException("Creation requires a new ledger seed without lifecycle facts");
        }
        requireOptionalBalance(row.getOccupiedBaseQty(), row.getCoveredDemandBaseQty(), "occupied_base_qty");
        requireOptionalBalance(row.getFulfilledBaseQty(), ZERO, "fulfilled_base_qty");
        requireOptionalBalance(row.getReleasedBaseQty(), ZERO, "released_base_qty");

        row.setAllocationStatus(REQUEST_INTENT);
        row.setOccupiedBaseQty(row.getCoveredDemandBaseQty());
        row.setFulfilledBaseQty(ZERO);
        row.setReleasedBaseQty(ZERO);
        row.setVersion(0);
        row.setCreateTime(copyDate(timestamp));
        row.setUpdateTime(copyDate(timestamp));
        return new Change(null, row, timestamp);
    }

    /**
     * 记录由调用方核验的真实订单行对未转单意向的继承。
     * 占用量保持不变：订单草稿既不是库存，也不是准时供给。
     * 返回版本仍是 CAS 所期望的版本，不是递增后的版本。
     *
     * @param ledger 尚未转单的申请意向账本快照
     * @param orderLineId 由调用方独立核验的真实订单行标识
     * @param now 本次变更时间
     * @return 保持占用量及期望 CAS 版本的转单变更快照
     */
    public static Change commitToOrder(PuRequirementAllocationLedger ledger, Long orderLineId, Date now) {
        Date timestamp = requireNow(now);
        requirePositiveId(orderLineId, "order_line_id");
        PuRequirementAllocationLedger before = checkedCopy(ledger);
        requireRequestIntent(before);
        PuRequirementAllocationLedger row = copyLedger(before);
        row.setOrderLineId(orderLineId);
        row.setAllocationStatus(ORDER_COMMITTED);
        row.setTransferTime(copyDate(timestamp));
        row.setUpdateTime(copyDate(timestamp));
        return new Change(before, row, timestamp);
    }

    /**
     * 仅释放尚未转单的意向；来源采购申请关闭不能绕过此限制。
     * 此有界意向的履约量为零且无历史释放，因而全部覆盖量就是未履约余量。
     * 此处不应用部分履约算法。
     *
     * @param ledger 尚未转单的申请意向账本快照
     * @param now 本次变更时间
     * @param reason 释放原因
     * @return 供调用方持久化的完整意向释放变更快照
     */
    public static Change releaseRequestIntent(PuRequirementAllocationLedger ledger, Date now, String reason) {
        Date timestamp = requireNow(now);
        requireText(reason, 255, "release_reason");
        PuRequirementAllocationLedger before = checkedCopy(ledger);
        requireRequestIntent(before);
        PuRequirementAllocationLedger row = copyLedger(before);
        row.setAllocationStatus(RELEASED);
        row.setOccupiedBaseQty(ZERO);
        row.setReleasedBaseQty(row.getCoveredDemandBaseQty());
        row.setReleasedAt(copyDate(timestamp));
        row.setReleaseReason(reason);
        row.setUpdateTime(copyDate(timestamp));
        return new Change(before, row, timestamp);
    }

    /** 申请审批不增加来源额度；真实审批结果、精确批准量和权限由生命周期服务核验。 */
    public static Change approveRequestIntent(PuRequirementAllocationLedger ledger, Date now) {
        Date timestamp = requireNow(now);
        PuRequirementAllocationLedger before = checkedCopy(ledger);
        requireRequestIntent(before);
        PuRequirementAllocationLedger row = copyLedger(before);
        row.setUpdateTime(copyDate(timestamp));
        return new Change(before, row, timestamp);
    }

    /** 仅已核验入账的部分收货可由内部服务调用；参数不构成外部数量授权。 */
    public static Change receiveOrder(PuRequirementAllocationLedger ledger, BigDecimal quantity, Date now) {
        Date timestamp = requireNow(now);
        PuRequirementAllocationLedger before = checkedCopy(ledger);
        requireOrderBalance(before, false);
        requirePositiveQuantity(quantity);
        if (quantity.compareTo(before.getOccupiedBaseQty()) > 0) {
            throw new IllegalArgumentException("Receipt exceeds the unfulfilled occupied balance");
        }
        PuRequirementAllocationLedger row = copyLedger(before);
        row.setOccupiedBaseQty(before.getOccupiedBaseQty().subtract(quantity));
        row.setFulfilledBaseQty(before.getFulfilledBaseQty().add(quantity));
        row.setUpdateTime(copyDate(timestamp));
        return new Change(before, row, timestamp);
    }

    /** 订单结案只释放未履约占用，保留订单承接与已入账履约证据。 */
    public static Change releaseOrder(PuRequirementAllocationLedger ledger, Date now, String reason) {
        Date timestamp = requireNow(now);
        requireText(reason, 255, "release_reason");
        PuRequirementAllocationLedger before = checkedCopy(ledger);
        requireOrderBalance(before, false);
        PuRequirementAllocationLedger row = copyLedger(before);
        row.setAllocationStatus(RELEASED);
        row.setReleasedBaseQty(before.getReleasedBaseQty().add(before.getOccupiedBaseQty()));
        row.setOccupiedBaseQty(ZERO);
        row.setReleasedAt(copyDate(timestamp));
        row.setReleaseReason(reason);
        row.setUpdateTime(copyDate(timestamp));
        return new Change(before, row, timestamp);
    }

    /**
     * 已执行退货减少实际履约；开放订单恢复原来源占用，结案订单增加释放而不复活。
     * 幂等事件与真实退货过账由生命周期服务核验，策略不自行判断退货发生。
     */
    public static Change returnOrder(PuRequirementAllocationLedger ledger, BigDecimal quantity, Date now) {
        Date timestamp = requireNow(now);
        PuRequirementAllocationLedger before = checkedCopy(ledger);
        requireOrderBalance(before, true);
        requirePositiveQuantity(quantity);
        if (quantity.compareTo(before.getFulfilledBaseQty()) > 0) {
            throw new IllegalArgumentException("Return exceeds the persisted fulfilled balance");
        }
        PuRequirementAllocationLedger row = copyLedger(before);
        row.setFulfilledBaseQty(before.getFulfilledBaseQty().subtract(quantity));
        if (RELEASED.equals(before.getAllocationStatus())) {
            row.setReleasedBaseQty(before.getReleasedBaseQty().add(quantity));
        } else {
            row.setOccupiedBaseQty(before.getOccupiedBaseQty().add(quantity));
        }
        row.setUpdateTime(copyDate(timestamp));
        return new Change(before, row, timestamp);
    }

    private static void requirePositiveQuantity(BigDecimal quantity) {
        requireQuantity(quantity, "posted_base_qty");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Posted base quantity must be positive");
        }
    }

    private static void requireOrderBalance(PuRequirementAllocationLedger row, boolean allowReleased) {
        requirePositiveId(row.getOrderLineId(), "order_line_id");
        if (row.getTransferTime() == null || row.getCreateTime() == null
            || row.getVersion() == null || row.getVersion() < 0 || row.getVersion() == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Order lifecycle identity, time or CAS version is unknown");
        }
        requireQuantity(row.getOccupiedBaseQty(), "occupied_base_qty");
        requireQuantity(row.getFulfilledBaseQty(), "fulfilled_base_qty");
        requireQuantity(row.getReleasedBaseQty(), "released_base_qty");
        if (row.getCoveredDemandBaseQty().compareTo(row.getOccupiedBaseQty()
            .add(row.getFulfilledBaseQty()).add(row.getReleasedBaseQty())) != 0) {
            throw new IllegalArgumentException("Covered demand must equal occupied + fulfilled + released");
        }
        if (ORDER_COMMITTED.equals(row.getAllocationStatus())) {
            if (row.getReleasedBaseQty().signum() != 0 || row.getReleasedAt() != null || row.getReleaseReason() != null) {
                throw new IllegalArgumentException("An active order cannot contain release facts");
            }
        } else if (allowReleased && RELEASED.equals(row.getAllocationStatus())) {
            requireText(row.getReleaseReason(), 255, "release_reason");
            if (row.getOccupiedBaseQty().signum() != 0 || row.getReleasedAt() == null) {
                throw new IllegalArgumentException("A closed order must have zero occupancy and release facts");
            }
        } else {
            throw new IllegalArgumentException("Unsupported order lifecycle state");
        }
    }

    /** 供事件消费者使用的不可变快照；构造时及每次访问时均对可变值制作独立副本。 */
    public static final class Change {
        private final PuRequirementAllocationLedger before;
        private final PuRequirementAllocationLedger ledger;
        private final Date occurredAt;

        private Change(PuRequirementAllocationLedger before, PuRequirementAllocationLedger ledger, Date occurredAt) {
            this.before = copyLedger(before);
            this.ledger = copyLedger(ledger);
            this.occurredAt = copyDate(occurredAt);
        }

        /** 变更前账本快照；新建时为 null，绝不引用调用方持有的状态。 */
        public PuRequirementAllocationLedger before() {
            return copyLedger(before);
        }

        /** 供调用方控制持久化的结果快照；修改该快照不会改变此变更对象。 */
        public PuRequirementAllocationLedger ledger() {
            return copyLedger(ledger);
        }

        public Date occurredAt() {
            return copyDate(occurredAt);
        }
    }

    private static PuRequirementAllocationLedger checkedCopy(PuRequirementAllocationLedger ledger) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger is required");
        }
        PuRequirementAllocationLedger row = copyLedger(ledger);
        requirePositiveId(row.getAllocationId(), "allocation_id");
        requireIdentityText(row.getTenantId(), 20, "tenant_id");
        requirePositiveId(row.getMaterialId(), "material_id");
        if (!MRP_DETAIL.equals(row.getSourceType())) {
            throw new IllegalArgumentException("source_type must explicitly be MRP_DETAIL");
        }
        requirePositiveId(row.getDetailId(), "detail_id");
        requirePositiveId(row.getRequisitionLineId(), "requisition_line_id");
        if (row.getAllocationNo() == null || row.getAllocationNo() <= 0) {
            throw new IllegalArgumentException("allocation_no must be present and positive");
        }
        requirePositiveId(row.getBaseUomId(), "base_uom_id");
        requireIdentityText(row.getAllocationKey(), 128, "allocation_key");
        if (row.getDecisionIdempotencyKey() != null) {
            requireIdentityText(row.getDecisionIdempotencyKey(), 128, "decision_idempotency_key");
        }
        requireQuantity(row.getCoveredDemandBaseQty(), "covered_demand_base_qty");
        if (row.getCoveredDemandBaseQty().signum() <= 0) {
            throw new IllegalArgumentException("covered_demand_base_qty must be positive");
        }
        return row;
    }

    private static void requireRequestIntent(PuRequirementAllocationLedger row) {
        requireUncommitted(row);
        if (!REQUEST_INTENT.equals(row.getAllocationStatus()) || row.getCreateTime() == null) {
            throw new IllegalArgumentException("Transition requires an existing REQUEST_INTENT");
        }
        if (row.getVersion() == null || row.getVersion() < 0 || row.getVersion() == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("version must support the caller's next CAS increment");
        }
        requireQuantity(row.getOccupiedBaseQty(), "occupied_base_qty");
        requireQuantity(row.getFulfilledBaseQty(), "fulfilled_base_qty");
        requireQuantity(row.getReleasedBaseQty(), "released_base_qty");
        if (row.getOccupiedBaseQty().compareTo(row.getCoveredDemandBaseQty()) != 0
            || row.getFulfilledBaseQty().signum() != 0 || row.getReleasedBaseQty().signum() != 0) {
            throw new IllegalArgumentException("REQUEST_INTENT must have full occupancy and no fulfillment or release");
        }
    }

    private static void requireUncommitted(PuRequirementAllocationLedger row) {
        if (row.getOrderLineId() != null || row.getTransferTime() != null
            || row.getReleasedAt() != null || row.getReleaseReason() != null) {
            throw new IllegalArgumentException("Order inheritance or release facts forbid an intent-only transition");
        }
    }

    private static void requireOptionalBalance(BigDecimal value, BigDecimal expected, String field) {
        if (value != null) {
            requireQuantity(value, field);
            if (value.compareTo(expected) != 0) {
                throw new IllegalArgumentException(field + " contradicts the new intent balance");
            }
        }
    }

    /** 仅校验 DECIMAL(18,4)：尾随零保持精确；任何值都不舍入，也不强制转换。 */
    private static void requireQuantity(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be present and nonnegative");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > QUANTITY_SCALE
            || (long) normalized.precision() - normalized.scale() > QUANTITY_INTEGER_DIGITS) {
            throw new IllegalArgumentException(field + " must fit DECIMAL(18,4) exactly without overflow");
        }
    }

    private static void requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be present and positive");
        }
    }

    private static void requireIdentityText(String value, int maxLength, String field) {
        requireText(value, maxLength, field);
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not contain surrounding whitespace");
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(field + " must be nonblank and fit its existing column");
        }
    }

    private static Date requireNow(Date now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        return copyDate(now);
    }

    private static Date copyDate(Date value) {
        return value == null ? null : new Date(value.getTime());
    }

    private static PuRequirementAllocationLedger copyLedger(PuRequirementAllocationLedger value) {
        if (value == null) {
            return null;
        }
        PuRequirementAllocationLedger copy = new PuRequirementAllocationLedger();
        copy.setAllocationId(value.getAllocationId());
        copy.setTenantId(value.getTenantId());
        copy.setMaterialId(value.getMaterialId());
        copy.setSourceType(value.getSourceType());
        copy.setDetailId(value.getDetailId());
        copy.setRequisitionLineId(value.getRequisitionLineId());
        copy.setAllocationNo(value.getAllocationNo());
        copy.setOrderLineId(value.getOrderLineId());
        copy.setBaseUomId(value.getBaseUomId());
        copy.setCoveredDemandBaseQty(value.getCoveredDemandBaseQty());
        copy.setOccupiedBaseQty(value.getOccupiedBaseQty());
        copy.setFulfilledBaseQty(value.getFulfilledBaseQty());
        copy.setReleasedBaseQty(value.getReleasedBaseQty());
        copy.setAllocationStatus(value.getAllocationStatus());
        copy.setAllocationKey(value.getAllocationKey());
        copy.setDecisionIdempotencyKey(value.getDecisionIdempotencyKey());
        copy.setVersion(value.getVersion());
        copy.setCreateTime(copyDate(value.getCreateTime()));
        copy.setTransferTime(copyDate(value.getTransferTime()));
        copy.setReleasedAt(copyDate(value.getReleasedAt()));
        copy.setReleaseReason(value.getReleaseReason());
        copy.setUpdateTime(copyDate(value.getUpdateTime()));
        return copy;
    }
}
