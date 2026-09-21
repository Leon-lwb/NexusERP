package com.nexus.iot.alert.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * IoT 告警条件判定（比较符语义的唯一实现，IOT-ALERT-02）。
 * <p>
 * 触发与恢复共用同一判定函数，避免"触发用 &gt;、恢复用 &lt;"两处口径漂移：
 * 触发时 {@code breached(...) == true}，恢复时 {@code breached(...) == false}。
 *
 * @author nexus
 */
public final class IotAlertCondition {

    /** 大于 */
    public static final String GT = "GT";
    /** 大于等于 */
    public static final String GE = "GE";
    /** 小于 */
    public static final String LT = "LT";
    /** 小于等于 */
    public static final String LE = "LE";
    /** 等于 */
    public static final String EQ = "EQ";
    /** 不等于 */
    public static final String NE = "NE";

    /** 允许的比较符集合 */
    public static final Set<String> OPERATORS = Set.of(GT, GE, LT, LE, EQ, NE);

    private IotAlertCondition() {
    }

    /**
     * 比较符是否合法（大小写不敏感）。
     *
     * @param operator 比较符
     * @return 合法返回 true
     */
    public static boolean isValidOperator(String operator) {
        return operator != null && OPERATORS.contains(operator.trim().toUpperCase());
    }

    /**
     * 归一化比较符：非法或为空时回落 GT（与原硬编码语义一致）。
     *
     * @param operator 比较符
     * @return 归一化后的比较符
     */
    public static String normalizeOperator(String operator) {
        return isValidOperator(operator) ? operator.trim().toUpperCase() : GT;
    }

    /**
     * 判定当前值是否越过告警条件。
     *
     * @param operator  比较符（非法时按 GT 处理）
     * @param value     当前值，null 返回 false
     * @param threshold 阈值，null 返回 false
     * @return 越限返回 true
     */
    public static boolean breached(String operator, BigDecimal value, BigDecimal threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        int cmp = value.compareTo(threshold);
        return switch (normalizeOperator(operator)) {
            case GE -> cmp >= 0;
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            default -> cmp > 0;
        };
    }

}
