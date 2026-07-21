package com.nexus.erp.pu.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 采购订单状态枚举
 * <p>定义采购订单全生命周期状态及其合法流转规则，供采购订单状态机使用。</p>
 */
public enum PurchaseOrderStatusEnum {

    /** 草稿：采购订单刚创建，尚未提交审批 */
    DRAFT("DRAFT", "草稿"),
    /** 已提交：已提交审批流，等待审批 */
    SUBMITTED("SUBMITTED", "已提交"),
    /** 审批中：审批流程进行中 */
    APPROVING("APPROVING", "审批中"),
    /** 已批准：审批通过，可下达给供应商 */
    APPROVED("APPROVED", "已批准"),
    /** 部分收货：已收货但未全部到货 */
    PARTIALLY_RECEIVED("PARTIALLY_RECEIVED", "部分收货"),
    /** 已收货：全部到货完成 */
    RECEIVED("RECEIVED", "已收货"),
    /** 已关闭：订单关闭，不再收货 */
    CLOSED("CLOSED", "已关闭"),
    /** 已作废：订单取消作废 */
    CANCELLED("CANCELLED", "已作废"),
    /** 已驳回：审批被驳回，可重新提交 */
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String label;

    PurchaseOrderStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取状态编码
     *
     * @return 状态编码
     */
    public String getCode() { return code; }

    /**
     * 获取状态中文标签
     *
     * @return 状态标签
     */
    public String getLabel() { return label; }

    /**
     * 判断当前状态是否可流转到目标状态。
     *
     * @param target 目标状态
     * @return 允许流转返回 true，否则 false
     */
    public boolean canTransitionTo(PurchaseOrderStatusEnum target) {
        Set<PurchaseOrderStatusEnum> allowed = switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(APPROVING, DRAFT, CANCELLED);
            case APPROVING -> EnumSet.of(APPROVED, REJECTED, DRAFT);
            case APPROVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED);
            case PARTIALLY_RECEIVED -> EnumSet.of(RECEIVED, CLOSED);
            case RECEIVED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(PurchaseOrderStatusEnum.class);
            case CANCELLED -> EnumSet.noneOf(PurchaseOrderStatusEnum.class);
            case REJECTED -> EnumSet.of(DRAFT);
        };
        return allowed.contains(target);
    }

    /**
     * 判断是否为终态（已关闭或已作废，不可再流转）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /**
     * 根据状态编码获取枚举，编码为空返回 null；未知编码抛异常。
     *
     * @param code 状态编码
     * @return 对应的采购订单状态枚举
     */
    public static PurchaseOrderStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderStatusEnum s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知采购订单状态: " + code);
    }
}
