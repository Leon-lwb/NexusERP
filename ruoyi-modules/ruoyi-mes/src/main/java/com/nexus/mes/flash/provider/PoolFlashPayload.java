package com.nexus.mes.flash.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.utils.StringUtils;

/**
 * 池类刷写(MAC/SN 资源池)内容载荷工具
 * <p>池类刷写的内容载荷格式固定为单键 JSON(如 {@code {"mac":"00:1A:2B:3C:4D:5E"}})。
 * 应刷值由池分配产生, 实刷值由设备上报携带(见 MES-FLASH-02): 二者须比对并把实刷值落库供追溯。</p>
 *
 * @author Nexus
 */
public final class PoolFlashPayload {

    /** 载荷解析用独立 mapper(不参与 API 序列化, 保持工具类可单测) */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PoolFlashPayload() {
    }

    /** 构建单键内容载荷 JSON */
    public static String of(String field, String value) {
        return "{\"" + field + "\":\"" + (value == null ? "" : value) + "\"}";
    }

    /**
     * 从内容载荷 JSON 中提取字段值
     *
     * @param payloadJson 载荷 JSON(可为空/非法, 均返回 null)
     * @param field       字段名(池类: mac / sn)
     * @return 字段值; 缺失或无法解析返回 null
     */
    public static String extract(String payloadJson, String field) {
        if (StringUtils.isBlank(payloadJson) || StringUtils.isBlank(field)) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(payloadJson);
            JsonNode value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            String text = value.asText();
            return StringUtils.isBlank(text) ? null : text;
        } catch (Exception e) {
            return null;
        }
    }
}
