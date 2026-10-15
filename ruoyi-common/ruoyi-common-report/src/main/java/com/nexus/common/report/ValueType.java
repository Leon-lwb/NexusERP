package com.nexus.common.report;

/** 稳定的传输类型；标识符和十进制数以精确字符串传输。 */
public enum ValueType {
    /** 保存当前 {@code STRING} 值。 */
    STRING,
    /** 保存当前 {@code ID} 值。 */
    ID,
    /** 保存当前 {@code INTEGER} 值。 */
    INTEGER,
    /** 保存当前 {@code DECIMAL} 值。 */
    DECIMAL,
    /** 保存当前 {@code BOOLEAN} 值。 */
    BOOLEAN,
    /** 保存当前 {@code DATE} 值。 */
    DATE,
    /** 保存当前 {@code DATETIME} 值。 */
    DATETIME
}
