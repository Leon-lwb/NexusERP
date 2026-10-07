package com.nexus.flow.node.ai;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.state.FlowTemplateRenderer;
import com.nexus.ai.service.rag.RetrieverService;
import com.nexus.common.ai.decision.dto.RetrievalHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 检索节点执行器
 * <p>从知识库检索相关片段，输出 chunks 列表 + 增强提示词</p>
 *
 * @author NexusERP
 */
@Component
@RequiredArgsConstructor
public class RagNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RetrieverService retrieverService;

    @Override
    public String getNodeType() {
        return "rag";
    }

    @Override
    public String getDisplayName() {
        return "RAG 检索";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema knowledgeId = new NodeParamSchema("knowledgeId", "知识库", "number", "0", true);
        knowledgeId.setOptionsSource("aiKnowledge");
        return List.of(
            knowledgeId,
            new NodeParamSchema("queryTemplate", "查询模板", "textarea", "", true),
            new NodeParamSchema("topK", "返回条数", "number", "5", false),
            new NodeParamSchema("minScore", "相似度阈值", "number", "0.5", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        AiNodeEntityIdReader.read(config, "knowledgeId", "RAG");
        if (config.get("queryTemplate") == null || config.get("queryTemplate").asText().isBlank()) {
            throw new IllegalArgumentException("RAG 节点缺少 queryTemplate 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        Long knowledgeId = AiNodeEntityIdReader.read(config, "knowledgeId", "RAG");

        // 渲染查询模板
        String queryTemplate = config.get("queryTemplate").asText();
        String query = FlowTemplateRenderer.render(queryTemplate, ctx.getState(),
            "节点 " + ctx.getNodeName() + " 的 queryTemplate");

        // 可选参数覆盖
        java.util.Map<String, Object> overrides = null;
        if (config.has("topK") && !config.get("topK").isNull()) {
            overrides = new java.util.HashMap<>();
            overrides.put("topK", config.get("topK").asInt());
        }
        if (config.has("minScore") && !config.get("minScore").isNull()) {
            if (overrides == null) {
                overrides = new java.util.HashMap<>();
            }
            overrides.put("minScore", config.get("minScore").asDouble());
        }

        List<RetrievalHit> hits = retrieverService.retrieveHits(knowledgeId, query, overrides);
        List<String> chunks = hits.stream().map(RetrievalHit::content).toList();

        // 保留结构化来源；显式转字符串，避免雪花 ID 绕过全局 Jackson 序列化器。
        ObjectNode output = MAPPER.createObjectNode();
        ArrayNode hitsNode = output.putArray("hits");
        for (RetrievalHit hit : hits) {
            ObjectNode item = hitsNode.addObject();
            item.put("knowledgeId", String.valueOf(hit.knowledgeId()));
            item.put("documentId", String.valueOf(hit.documentId()));
            item.put("chunkId", String.valueOf(hit.chunkId()));
            item.put("documentName", hit.documentName());
            item.put("content", hit.content());
            if (hit.rerankScore() != null) item.put("rerankScore", hit.rerankScore());
            item.put("sourceType", hit.sourceType());
            item.put("version", hit.version());
            if (hit.effectiveDate() != null) item.put("effectiveDate", hit.effectiveDate().toString());
        }
        ArrayNode chunksNode = output.putArray("chunks");
        chunks.forEach(chunksNode::add);
        output.put("count", chunks.size());
        output.put("augmentedPrompt", buildAugmentedPrompt(chunks, query));
        return output;
    }

    /**
     * 构建增强提示词（对齐 RetrieverService.buildAugmentedPrompt 的既有格式）
     *
     * @param chunks 检索片段
     * @param query  查询
     * @return 增强提示词
     */
    private String buildAugmentedPrompt(List<String> chunks, String query) {
        if (chunks == null || chunks.isEmpty()) {
            return "未检索到相关资料，请直接回答：" + query;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下参考资料回答问题。若资料无法回答，请明确说明。\n\n【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append(i + 1).append(". ").append(chunks.get(i)).append("\n\n");
        }
        sb.append("【问题】\n").append(query);
        return sb.toString();
    }
}
