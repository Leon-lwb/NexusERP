package com.nexus.erp.pu.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 采购订单状态枚举
 */
public enum PurchaseOrderStatusEnum {

    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVING("APPROVING", "审批中"),
    APPROVED("APPROVED", "已批准"),
    PARTIALLY_RECEIVED("PARTIALLY_RECEIVED", "部分收货"),
    RECEIVED("RECEIVED", "已收货"),
    CLOSED("CLOSED", "已关闭"),
    CANCELLED("CANCELLED", "已作废"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String label;

    PurchaseOrderStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

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

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    public static PurchaseOrderStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderStatusEnum s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知采购订单状态: " + code);
    }
}
