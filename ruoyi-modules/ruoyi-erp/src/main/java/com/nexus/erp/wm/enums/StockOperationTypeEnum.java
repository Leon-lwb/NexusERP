package com.nexus.erp.wm.enums;

/**
 * 库存操作类型枚举
 * <p>
 * 定义系统支持的所有库存操作类型，包括入库、出库、调拨、盘点等。
 *
 * @author nexus
 */
public enum StockOperationTypeEnum {

    /** 采购入库 */
    RECEIVE_IN("RECEIVE_IN", "采购入库"),
    /** 销售出库 */
    SALES_OUT("SALES_OUT", "销售出库"),
    /** 调拨 */
    TRANSFER("TRANSFER", "调拨"),
    /** 盘点调整 */
    COUNT_ADJUST("COUNT_ADJUST", "盘点调整"),
    /** 杂项出库 */
    MISC_ISSUE("MISC_ISSUE", "杂项出库"),
    /** 杂项入库 */
    MISC_RECPT("MISC_RECPT", "杂项入库"),
    /** 生产领料 */
    PRODUCTION_ISSUE("PRODUCTION_ISSUE", "生产领料"),
    /** 完工入库 */
    PRODUCTION_IN("PRODUCTION_IN", "完工入库"),
    /** 委外发料 */
    SUBCONTRACT_ISSUE("SUBCONTRACT_ISSUE", "委外发料"),
    /** 委外收货 */
    SUBCONTRACT_RECEIVE("SUBCONTRACT_RECEIVE", "委外收货"),
    /** 返工出库 */
    REWORK_ISSUE("REWORK_ISSUE", "返工出库"),
    /** 返工入库 */
    REWORK_RECEIVE("REWORK_RECEIVE", "返工入库");

    /** 操作类型编码 */
    private final String code;
    /** 操作类型描述 */
    private final String desc;

    /**
     * 构造库存操作类型枚举
     *
     * @param code 操作类型编码
     * @param desc 操作类型描述
     */
    StockOperationTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取操作类型编码
     *
     * @return 操作类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取操作类型描述
     *
     * @return 操作类型描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据编码获取对应的枚举值
     *
     * @param code 操作类型编码
     * @return 对应的枚举值，找不到则返回null
     */
    public static StockOperationTypeEnum fromCode(String code) {
        for (StockOperationTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
