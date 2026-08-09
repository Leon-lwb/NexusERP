package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OSS 文件节点
 * <p>UPLOAD_TEXT：把渲染后的文本内容写入 OSS（默认 .txt），输出路径/URL；
 * GET_CONTENT：读取 OSS 文件内容为文本，供下游 LLM/处理节点使用。
 * 复用 OssFactory.instance()（MinIO 默认配置）</p>
 *
 * @author NexusERP
 */
@Component
public class OssNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "oss";
    }

    @Override
    public String getDisplayName() {
        return "OSS 文件";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema action = new NodeParamSchema("action", "操作", "options", "UPLOAD_TEXT", true);
        action.setOptions(List.of(
            Map.of("name", "上传文本", "value", "UPLOAD_TEXT"),
            Map.of("name", "读取内容", "value", "GET_CONTENT")
        ));
        return List.of(
            action,
            new NodeParamSchema("fileName", "文件名 ({{}})", "string", "", false),
            new NodeParamSchema("content", "文本内容 ({{}})", "textarea", "", false),
            new NodeParamSchema("filePath", "文件路径 ({{}})", "string", "", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("action") == null || config.get("action").asText().isBlank()) {
            throw new IllegalArgumentException("OSS 节点缺少 action 配置");
        }
        String action = config.get("action").asText();
        if ("GET_CONTENT".equals(action)
            && (config.get("filePath") == null || config.get("filePath").asText().isBlank())) {
            throw new IllegalArgumentException("OSS 读取模式缺少 filePath 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) throws Exception {
        JsonNode config = ctx.getConfig();
        String action = config.get("action").asText();
        String label = "节点 " + ctx.getNodeName();
        OssClient client = OssFactory.instance();

        ObjectNode output = MAPPER.createObjectNode();
        if ("UPLOAD_TEXT".equals(action)) {
            // 渲染文件名与内容（支持 {{}} 变量引用）；key 用 fileName 保证读回一致（uploadSuffix 会生成随机 key）
            String fileName = config.has("fileName") && !config.get("fileName").asText().isBlank()
                ? FlowTemplateRenderer.render(config.get("fileName").asText(), ctx.getState(), label + " 的 fileName")
                : "flow-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
            String content = config.has("content")
                ? FlowTemplateRenderer.render(config.get("content").asText(), ctx.getState(), label + " 的 content")
                : "";
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            var result = client.upload(new java.io.ByteArrayInputStream(data), fileName, (long) data.length, "text/plain");
            output.put("success", true);
            output.put("fileName", fileName);
            if (result != null && result.getUrl() != null) {
                output.put("url", result.getUrl());
            }
            output.put("size", data.length);
        } else if ("GET_CONTENT".equals(action)) {
            String filePath = FlowTemplateRenderer.render(config.get("filePath").asText(), ctx.getState(), label + " 的 filePath");
            try (InputStream in = client.getObjectContent(filePath)) {
                if (in == null) {
                    throw new IllegalArgumentException("OSS 文件不存在: " + filePath);
                }
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                output.put("success", true);
                output.put("filePath", filePath);
                output.put("content", content);
                output.put("size", content.length());
            }
        } else {
            throw new IllegalArgumentException("不支持的 OSS 操作: " + action);
        }
        return output;
    }
}
