package org.dromara.system.domain.enums;

public enum CycleMethodEnum {

    CYCLE_METHOD_YEAR("YEAR", "按年"),
    CYCLE_METHOD_MONTH("MONTH", "按月"),
    CYCLE_METHOD_DAY("DAY", "按日"),
    CYCLE_METHOD_HOUR("HOUR", "按小时"),
    CYCLE_METHOD_MINUTE("MINUTE", "按分钟"),
    CYCLE_METHOD_OTHER("OTHER", "其他");

    private final String code;
    private final String name;

    CycleMethodEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public static CycleMethodEnum getByCode(String code) {
        for (CycleMethodEnum value : CycleMethodEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return CYCLE_METHOD_OTHER;
    }
}
