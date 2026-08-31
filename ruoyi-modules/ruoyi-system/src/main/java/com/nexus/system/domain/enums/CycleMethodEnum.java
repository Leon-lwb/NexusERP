package com.nexus.system.domain.enums;

/**
 * 自动编码组成部分的重置周期方式枚举。
 * <p>定义流水号在何种时间周期结束后归零重置，属于系统管理模块。</p>
 */
public enum CycleMethodEnum {

    /** 按年重置 */
    CYCLE_METHOD_YEAR("YEAR", "按年"),
    /** 按月重置 */
    CYCLE_METHOD_MONTH("MONTH", "按月"),
    /** 按日重置 */
    CYCLE_METHOD_DAY("DAY", "按日"),
    /** 按小时重置 */
    CYCLE_METHOD_HOUR("HOUR", "按小时"),
    /** 按分钟重置 */
    CYCLE_METHOD_MINUTE("MINUTE", "按分钟"),
    /** 其他（不重置） */
    CYCLE_METHOD_OTHER("OTHER", "其他");

    /** 周期方式编码 */
    private final String code;
    /** 周期方式名称 */
    private final String name;

    CycleMethodEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 获取周期方式编码。
     *
     * @return 编码
     */
    public String getCode() { return code; }

    /**
     * 获取周期方式名称。
     *
     * @return 名称
     */
    public String getName() { return name; }

    /**
     * 根据编码查找对应的周期方式枚举，未匹配时返回 {@code OTHER}。
     *
     * @param code 周期方式编码
     * @return 匹配的枚举值
     */
    public static CycleMethodEnum getByCode(String code) {
        for (CycleMethodEnum value : CycleMethodEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return CYCLE_METHOD_OTHER;
    }
}
