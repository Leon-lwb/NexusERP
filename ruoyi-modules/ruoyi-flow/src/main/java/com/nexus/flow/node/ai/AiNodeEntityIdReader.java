package com.nexus.flow.node.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** AI 节点共用无损实体读取边界，兼容文本 ID、历史整型配置及原零值契约。 */
final class AiNodeEntityIdReader {
    private AiNodeEntityIdReader() {
    }

    static Long read(JsonNode config, String field, String nodeName) {
        JsonNode id = config == null ? null : config.get(field);
        if (id != null && id.isIntegralNumber() && id.canConvertToLong() && id.longValue() >= 0) {
            return id.longValue();
        }
        if (id != null && id.isTextual() && id.textValue().matches("0|[1-9]\\d*")) {
            try {
                return Long.valueOf(id.textValue());
            } catch (NumberFormatException ignored) {
                // 越界实体 ID 沿用节点原校验错误，不默认为零或其他实体。
            }
        }
        throw new IllegalArgumentException(nodeName + " 节点缺少 " + field + " 配置");
    }
}
