package com.nexus.ai.engineering.service;

import com.fasterxml.jackson.databind.*;

/** 内部有界元数据编码，不用于 HTTP 出参及雪花编号序列化。 */
public final class EngineeringJson {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private EngineeringJson() {}

    /** 将内部结构转换为规范 JSON。 */
    public static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("元数据无法编码", e);
        }
    }

    /** 读取内部结构。 */
    public static <T> T read(String value, Class<T> type) {
        try {
            return JSON.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("元数据无法解析", e);
        }
    }

    /** 读取有界 JSON 树。 */
    public static JsonNode tree(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("报告不是有效JSON", e);
        }
    }
}
