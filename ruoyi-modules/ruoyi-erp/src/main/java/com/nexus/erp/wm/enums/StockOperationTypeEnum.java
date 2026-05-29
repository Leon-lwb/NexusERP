package com.nexus.erp.wm.enums;

/**
 * 库存操作类型枚举
 */
public enum StockOperationTypeEnum {

    RECEIVE_IN("RECEIVE_IN", "采购入库"),
    SALES_OUT("SALES_OUT", "销售出库"),
    TRANSFER("TRANSFER", "调拨"),
    COUNT_ADJUST("COUNT_ADJUST", "盘点调整"),
    MISC_ISSUE("MISC_ISSUE", "杂项出库"),
    MISC_RECPT("MISC_RECPT", "杂项入库"),
    PRODUCTION_ISSUE("PRODUCTION_ISSUE", "生产领料"),
    PRODUCTION_IN("PRODUCTION_IN", "完工入库"),
    SUBCONTRACT_ISSUE("SUBCONTRACT_ISSUE", "委外发料"),
    SUBCONTRACT_RECEIVE("SUBCONTRACT_RECEIVE", "委外收货"),
    REWORK_ISSUE("REWORK_ISSUE", "返工出库"),
    REWORK_RECEIVE("REWORK_RECEIVE", "返工入库");

    private final String code;
    private final String desc;

    StockOperationTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static StockOperationTypeEnum fromCode(String code) {
        for (StockOperationTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
