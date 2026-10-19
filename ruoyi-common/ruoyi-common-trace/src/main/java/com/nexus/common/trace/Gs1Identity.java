package com.nexus.common.trace;

import java.util.*;
import java.util.regex.*;

/**
 * 识别现有终端使用的GS1身份字段；未知AI或缺分隔符不猜测后续SN。
 *
 * @param gtin 扫描载荷声明的全球贸易项目代码，用于主数据交叉校验。
 * @param batch 实际批次及其来源上下文。
 * @param serial 该业务行的逐件追踪选择。
 */
public record Gs1Identity(String gtin, String batch, String serial) {
    private static final Map<String, Integer> FIXED = Map.of("01", 14, "15", 6, "17", 6);
    private static final Map<String, Integer> VARIABLE = Map.of("10", 20, "21", 20, "37", 8);

    public static Optional<Gs1Identity> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String raw = input.trim();
        if (raw.startsWith("]C1") || raw.startsWith("]d2")) {
            raw = raw.substring(3);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (raw.startsWith("(")) {
            Matcher m = Pattern.compile("[(]([0-9]{2})[)]").matcher(raw);
            List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
            List<String> names = new ArrayList<>();
            while (m.find()) {
                starts.add(m.start());
                ends.add(m.end());
                names.add(m.group(1));
            }
            if (starts.isEmpty() || starts.get(0) != 0) {
                return Optional.empty();
            }
            for (int i = 0; i < starts.size(); i++) {
                String ai = names.get(i),
                        value =
                                raw.substring(
                                        ends.get(i),
                                        i + 1 < starts.size() ? starts.get(i + 1) : raw.length());
                if (!accept(fields, ai, value)) {
                    return Optional.empty();
                }
            }
        } else {
            if (!raw.startsWith("01") || raw.length() < 16) {
                return Optional.empty();
            }
            int cursor = 0;
            while (cursor < raw.length()) {
                if (raw.charAt(cursor) == '\u001d') {
                    cursor++;
                    continue;
                }
                if (cursor + 2 > raw.length()) {
                    return Optional.empty();
                }
                String ai = raw.substring(cursor, cursor + 2);
                cursor += 2;
                int end;
                if (FIXED.containsKey(ai)) {
                    end = cursor + FIXED.get(ai);
                } else if (VARIABLE.containsKey(ai)) {
                    end = raw.indexOf('\u001d', cursor);
                    if (end < 0) {
                        end = raw.length();
                    }
                } else {
                    return Optional.empty();
                }
                if (end > raw.length() || !accept(fields, ai, raw.substring(cursor, end))) {
                    return Optional.empty();
                }
                cursor = end;
            }
        }
        if (!fields.containsKey("01") && !fields.containsKey("10") && !fields.containsKey("21")) {
            return Optional.empty();
        }
        return Optional.of(new Gs1Identity(fields.get("01"), fields.get("10"), fields.get("21")));
    }

    private static boolean accept(Map<String, String> fields, String ai, String value) {
        if (fields.containsKey(ai) || value.isEmpty()) {
            return false;
        }
        if (FIXED.containsKey(ai)) {
            if (value.length() != FIXED.get(ai) || !value.matches("[0-9]+")) {
                return false;
            }
        } else if (VARIABLE.containsKey(ai)) {
            if (value.length() > VARIABLE.get(ai)) {
                return false;
            }
            if ("37".equals(ai) && !value.matches("[0-9]+")) {
                return false;
            }
        } else {
            return false;
        }
        fields.put(ai, value);
        return true;
    }
}
