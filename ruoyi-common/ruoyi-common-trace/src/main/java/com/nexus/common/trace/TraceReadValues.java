package com.nexus.common.trace;

import java.math.*;
import java.util.*;

/**
 * 即使分节保存为通用对象，业务标识和精确小数仍按字符串契约保留。
 */
public final class TraceReadValues {
    private TraceReadValues() {}

    public static Map<String, Object> wire(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>();
        row.forEach(
                (key, value) ->
                        result.put(
                                key.toLowerCase(Locale.ROOT),
                                value instanceof BigDecimal decimal
                                        ? decimal.toPlainString()
                                        : value instanceof Long || value instanceof BigInteger
                                                ? value.toString()
                                                : value));
        return Collections.unmodifiableMap(result);
    }

    public static Long id(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
