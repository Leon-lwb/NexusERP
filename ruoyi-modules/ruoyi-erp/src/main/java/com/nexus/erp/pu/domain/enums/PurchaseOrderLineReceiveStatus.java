package com.nexus.erp.pu.domain.enums;

/**
 * 采购订单行收货状态枚举
 * <p>标识采购订单明细行的收货执行情况，供收货入库与状态展示使用。</p>
 */
public enum PurchaseOrderLineReceiveStatus {
    /** 待收货：尚未收货 */
    PENDING("PENDING", "待收货"),
    /** 全收：收货数量达到订单数量 */
    FULL_RECEIVED("FULL_RECEIVED", "全收"),
    /** 部分收：已收货但未达订单数量 */
    PARTIAL_RECEIVED("PARTIAL_RECEIVED", "部分收"),
    /** 超收：收货数量超过订单数量 */
    OVER_RECEIVED("OVER_RECEIVED", "超收"),
    /** 短收：收货数量少于订单数量 */
    SHORT_RECEIVED("SHORT_RECEIVED", "短收");

    private final String code;
    private final String label;

    PurchaseOrderLineReceiveStatus(String code, String label) {
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
     * 根据状态编码获取枚举，编码为空返回 null；未知编码抛异常。
     *
     * @param code 状态编码
     * @return 对应的收货状态枚举
     */
    public static PurchaseOrderLineReceiveStatus fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderLineReceiveStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知行收货状态: " + code);
    }
}
