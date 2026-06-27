package com.nexus.erp.pu.domain.enums;

/**
 * 采购订单行收货状态
 */
public enum PurchaseOrderLineReceiveStatus {
    PENDING("PENDING", "待收货"),
    FULL_RECEIVED("FULL_RECEIVED", "全收"),
    PARTIAL_RECEIVED("PARTIAL_RECEIVED", "部分收"),
    OVER_RECEIVED("OVER_RECEIVED", "超收"),
    SHORT_RECEIVED("SHORT_RECEIVED", "短收");

    private final String code;
    private final String label;

    PurchaseOrderLineReceiveStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static PurchaseOrderLineReceiveStatus fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderLineReceiveStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知行收货状态: " + code);
    }
}
