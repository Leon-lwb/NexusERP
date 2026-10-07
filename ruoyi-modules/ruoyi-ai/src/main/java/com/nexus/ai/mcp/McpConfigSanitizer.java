package com.nexus.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.core.exception.ServiceException;
import org.springframework.beans.BeanUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/** MCP 配置的结构化脱敏与掩码回显恢复；此 Mapper 仅处理配置树，不序列化实体或雪花 ID。 */
public final class McpConfigSanitizer {
    public static final String MASK = "****";
    private static final ObjectMapper JSON = new ObjectMapper();

    private McpConfigSanitizer() {
    }

    public static AiTool maskTool(AiTool stored) {
        if (stored == null) {
            return null;
        }
        AiTool result = new AiTool();
        BeanUtils.copyProperties(stored, result);
        result.setConfigJson(maskJson(stored.getConfigJson()));
        return result;
    }

    public static AiMcpMarket maskMarket(AiMcpMarket stored) {
        if (stored == null) {
            return null;
        }
        AiMcpMarket result = new AiMcpMarket();
        BeanUtils.copyProperties(stored, result);
        result.setUrl(maskUrl(stored.getUrl()));
        if (stored.getAuthConfig() != null && !stored.getAuthConfig().isBlank()) {
            result.setAuthConfig(MASK);
        }
        return result;
    }

    public static AiMcpMarketTool maskMarketTool(AiMcpMarketTool stored) {
        AiMcpMarketTool result = new AiMcpMarketTool();
        BeanUtils.copyProperties(stored, result);
        result.setToolMetadata(maskJson(stored.getToolMetadata()));
        return result;
    }

    public static ObjectNode parseObject(String config) {
        try {
            JsonNode node = JSON.readTree(config);
            if (node instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception ignored) {
            // 解析器异常会携带配置原文，不能透传到 API 或日志。
        }
        throw new ServiceException("MCP configJson 必须是有效的 JSON 对象");
    }

    /** 传输环境变量和请求头均为字符串映射，拒绝静默忽略或强制转换错误结构。 */
    public static Map<String, String> stringMap(JsonNode value, String field) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Map.of();
        }
        if (!value.isObject()) {
            throw new ServiceException("MCP " + field + " 必须是字符串映射对象");
        }
        Map<String, String> result = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new ServiceException("MCP " + field + " 的值必须是字符串");
            }
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return result;
    }

    public static String maskJson(String config) {
        if (config == null || config.isBlank()) {
            return config;
        }
        try {
            JsonNode root = JSON.readTree(config);
            return maskNode(root, false).toString();
        } catch (Exception ignored) {
            return MASK;
        }
    }

    private static JsonNode maskNode(JsonNode node, boolean secret) {
        if (secret) {
            if (node.isObject()) {
                ObjectNode copy = ((ObjectNode) node).deepCopy();
                copy.fields().forEachRemaining(field -> copy.set(field.getKey(), maskNode(field.getValue(), true)));
                return copy;
            }
            return TextNode.valueOf(MASK);
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            copy.fields().forEachRemaining(field -> {
                String key = field.getKey();
                JsonNode value = field.getValue();
                if ("args".equalsIgnoreCase(key) && value.isArray()) {
                    copy.set(key, maskArguments((ArrayNode) value));
                } else {
                    boolean sensitive = isSensitiveName(key) || "env".equalsIgnoreCase(key)
                        || "headers".equalsIgnoreCase(key) || "authConfig".equalsIgnoreCase(key);
                    copy.set(key, maskNode(value, sensitive));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JSON.createArrayNode();
            node.forEach(value -> copy.add(maskNode(value, false)));
            return copy;
        }
        if (node.isTextual() && isUrl(node.asText())) {
            return TextNode.valueOf(maskUrl(node.asText()));
        }
        return node;
    }

    private static ArrayNode maskArguments(ArrayNode args) {
        ArrayNode masked = JSON.createArrayNode();
        boolean maskNext = false;
        for (JsonNode arg : args) {
            String value = arg.asText();
            int equals = value.indexOf('=');
            String flag = equals < 0 ? value : value.substring(0, equals);
            if (maskNext) {
                masked.add(MASK);
                maskNext = false;
            } else if (value.startsWith("-") && isSensitiveName(flag)) {
                masked.add(equals < 0 ? value : flag + "=" + MASK);
                maskNext = equals < 0;
            } else {
                masked.add(maskNode(arg, false));
            }
        }
        return masked;
    }

    private static boolean isSensitiveName(String key) {
        String name = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return name.endsWith("key") || name.endsWith("token") || name.endsWith("secret")
            || name.endsWith("password") || name.endsWith("passwd") || name.endsWith("authorization")
            || name.endsWith("credential") || name.endsWith("credentials");
    }

    private static boolean isUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8);
    }

    public static String maskUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            String output = value;
            if (uri.getRawUserInfo() != null) {
                output = output.replace(uri.getRawUserInfo() + "@", MASK + "@");
            }
            if (uri.getRawQuery() != null) {
                List<String> parts = new ArrayList<>();
                for (String part : uri.getRawQuery().split("&", -1)) {
                    int equals = part.indexOf('=');
                    parts.add(equals < 0 ? MASK : part.substring(0, equals + 1) + MASK);
                }
                int queryStart = output.indexOf('?');
                output = output.substring(0, queryStart + 1) + String.join("&", parts);
            }
            if (uri.getRawFragment() != null) {
                int fragmentStart = output.indexOf('#');
                if (fragmentStart >= 0) {
                    output = output.substring(0, fragmentStart) + "#" + MASK;
                }
            }
            return output;
        } catch (Exception ignored) {
            return MASK;
        }
    }

    /** 未改动的脱敏URL可原样恢复；部分编辑存在掩码歧义时明确拒绝，不能偷偷覆盖用户修改。 */
    public static String restoreMaskedUrl(String submitted, String stored) {
        if (submitted == null || !submitted.contains(MASK)) {
            return submitted;
        }
        if (stored != null && submitted.equals(maskUrl(stored))) {
            return stored;
        }
        throw new ServiceException("带掩码的MCP地址已修改，无法无歧义恢复，请重新填写完整地址");
    }

    /** 仅恢复提交树中含掩码的叶子，保留用户确实修改的非敏感字段。 */
    public static String restoreMasks(String submitted, String stored) {
        ObjectNode request = parseObject(submitted);
        if (!submitted.contains(MASK)) {
            return request.toString();
        }
        return restoreNode(request, parseObject(stored)).toString();
    }

    private static JsonNode restoreNode(JsonNode request, JsonNode stored) {
        if (request.isObject()) {
            ObjectNode result = ((ObjectNode) request).deepCopy();
            result.fields().forEachRemaining(field -> result.set(field.getKey(),
                restoreNode(field.getValue(), stored == null ? null : stored.get(field.getKey()))));
            return result;
        }
        if (request.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            for (int i = 0; i < request.size(); i++) {
                result.add(restoreNode(request.get(i), stored == null ? null : stored.get(i)));
            }
            return result;
        }
        if (request.isTextual() && request.asText().contains(MASK)) {
            if (stored == null || stored.isNull()) {
                throw new ServiceException("MCP 掩码字段没有可恢复的原配置，请重新填写该字段");
            }
            if (isUrl(request.asText()) || (stored.isTextual() && isUrl(stored.asText()))) {
                return TextNode.valueOf(restoreMaskedUrl(request.asText(), stored.asText()));
            }
            return stored.deepCopy();
        }
        return request;
    }
}
