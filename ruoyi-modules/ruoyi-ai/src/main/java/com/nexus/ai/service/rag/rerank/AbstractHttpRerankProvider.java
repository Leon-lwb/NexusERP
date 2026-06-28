package com.nexus.ai.service.rag.rerank;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.domain.entity.AiModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 调用型 Rerank 厂商基类
 * <p>百炼/智谱/硅基流动均通过 HTTP POST 调用各自的 rerank 接口，请求体结构相近
 * （model/query/documents/top_n/return_documents），响应为 results[]{index, relevance_score}，
 * 仅 URL 构造与鉴权头不同，故抽取公共逻辑到本基类</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
public abstract class AbstractHttpRerankProvider implements RerankProvider {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN, AiModel config) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        try {
            String url = buildUrl(config);
            String body = buildRequestBody(config.getModelName(), query, documents, topN);
            String responseJson = doRequest(url, body, config);
            List<RerankResult> results = parseResults(responseJson, documents);
            log.debug("[Rerank] {} 命中 {} 条, query={}", getProviderType(), results.size(), query);
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Rerank 调用失败[" + getProviderType() + "]: " + e.getMessage(), e);
        }
    }

    /** 构造请求 URL（各厂商端点不同） */
    protected abstract String buildUrl(AiModel config);

    /** 构造鉴权头（百炼/硅基为 Bearer apiKey，智谱为 Bearer JWT） */
    protected abstract String authHeader(AiModel config);

    /**
     * 构造请求体 JSON
     * <p>统一结构：{model, query, documents, top_n, return_documents:false}</p>
     */
    protected String buildRequestBody(String model, String query, List<String> documents, int topN) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", topN > 0 ? topN : documents.size());
        body.put("return_documents", false);
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 执行 HTTP POST 请求
     */
    protected String doRequest(String url, String body, AiModel config) {
        HttpResponse resp = HttpRequest.post(url)
            .header("Authorization", authHeader(config))
            .body(body, "application/json")
            .timeout(60000)
            .execute();
        if (!resp.isOk()) {
            throw new IllegalArgumentException("HTTP " + resp.getStatus() + " - " + resp.body());
        }
        return resp.body();
    }

    /**
     * 解析响应：兼容百炼 output.results 与智谱/硅基 results 两种结构
     * <p>按 relevance_score 降序排列</p>
     */
    protected List<RerankResult> parseResults(String json, List<String> documents) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.has("output") ? root.path("output").path("results") : root.path("results");
        List<RerankResult> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                int idx = item.path("index").asInt(-1);
                double score = item.path("relevance_score").asDouble(0.0);
                if (idx >= 0 && idx < documents.size()) {
                    list.add(new RerankResult(idx, score, documents.get(idx)));
                }
            }
        }
        list.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));
        return list;
    }
}
