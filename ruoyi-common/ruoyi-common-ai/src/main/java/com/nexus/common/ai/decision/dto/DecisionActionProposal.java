package com.nexus.common.ai.decision.dto;

import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strongly typed action/target IDs retain Long and use the global Jackson serializer.
 * Dynamic payload IDs must be decimal strings (including nested ID lists). Non-ID Long values
 * inside the JavaScript-safe range stay JSON numbers; larger Long values fail fast. BigDecimal
 * and BigInteger become plain decimal strings so the production Jackson modules cannot change
 * their types during round-trip serialization.
 */
public record DecisionActionProposal(
    Long actionId,
    DecisionActionType actionType,
    String title,
    String displayReason,
    DecisionBusinessRef target,
    Map<String, Object> payload,
    DecisionRiskLevel riskLevel,
    boolean approvalRequired,
    String permissionCode,
    String idempotencyKey,
    String executorCode,
    int sequence,
    List<Long> dependencyIds
) implements Serializable {

    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    /** Dynamic payload contract: identifiers are decimal strings, not JSON numbers. */
    public DecisionActionProposal {
        payload = copyPayload(payload);
        dependencyIds = dependencyIds == null ? null
            : Collections.unmodifiableList(new ArrayList<>(dependencyIds));
    }

    private static Map<String, Object> copyPayload(Map<?, ?> source) {
        if (source == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((rawKey, value) -> {
            if (!(rawKey instanceof String key)) {
                throw new IllegalArgumentException("动作 payload 的键必须是 String");
            }
            if (isIdKey(key)) {
                requireDecimalString(value, key);
            } else if (isIdListKey(key) && value != null) {
                if (!(value instanceof List<?> ids)) {
                    throw new IllegalArgumentException("动作 payload 中的 ID 列表必须是十进制字符串列表: " + key);
                }
                ids.forEach(id -> requireDecimalString(id, key));
            }
            copy.put(key, copyValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean isIdKey(String key) {
        return key.equalsIgnoreCase("id") || key.endsWith("Id") || key.endsWith("ID")
            || key.endsWith("_id") || key.endsWith("_ID");
    }

    private static boolean isIdListKey(String key) {
        return key.equalsIgnoreCase("ids") || key.endsWith("Ids") || key.endsWith("IDs")
            || key.endsWith("_ids") || key.endsWith("_IDS");
    }

    private static void requireDecimalString(Object value, String key) {
        if (value != null && (!(value instanceof String text) || !text.matches("[0-9]+"))) {
            throw new IllegalArgumentException("动作 payload 的 ID 必须以十进制字符串提供: " + key);
        }
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) return copyPayload(map);
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyValue(element)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Long number) {
            if (number > JS_MAX_SAFE_INTEGER || number < -JS_MAX_SAFE_INTEGER) {
                throw new IllegalArgumentException("动作 payload 的非 ID Long 超出 JS 安全整数范围；请使用明确类型");
            }
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return Integer.valueOf(number.intValue());
            }
            return number;
        }
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof BigInteger integer) return integer.toString();
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) throw new IllegalArgumentException("动作 payload 不接受非有限数值");
            return Double.valueOf(number.toString());
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) throw new IllegalArgumentException("动作 payload 不接受非有限数值");
            return number;
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
            || value instanceof Short || value instanceof Integer) {
            return value;
        }
        throw new IllegalArgumentException("动作 payload 包含不支持的值: " + value.getClass().getName());
    }
}
