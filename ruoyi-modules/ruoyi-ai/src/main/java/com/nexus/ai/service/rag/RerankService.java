package com.nexus.ai.service.rag;

import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 重排序服务
 * <p>对检索结果进行精排，提升相关性。Phase 1 采用 LLM 打分方案（Cross-Encoder 替代）</p>
 * <p>后续可升级为专用 Rerank API（智谱 glm-4-rerank、阿里百炼 gte-rerank、SiliconFlow BAAI/bge-reranker-v2-m3）</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final ChatModelFactory chatModelFactory;
    private final IAiModelService aiModelService;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /** 单个 chunk 打分超时时间（秒） */
    private static final int SCORE_TIMEOUT_SECONDS = 5;

    /** chunk 文本截断长度（限制 Token 消耗） */
    private static final int MAX_CHUNK_LENGTH = 500;

    /** 最大候选数（超过此数量截断，避免过多 LLM 调用） */
    private static final int MAX_CANDIDATES = 15;

    /**
     * 重排序文档片段
     * <p>使用 LLM 对每个 chunk 与 query 的相关性打分（0-10），按分数降序排列</p>
     *
     * @param query   查询文本
     * @param chunks  候选片段
     * @param topN    返回 Top N
     * @param modelId Rerank 模型ID（可为 null，使用默认 CHAT 模型）
     * @return 重排序后的片段列表（按相关性降序）
     */
    public List<String> rerank(String query, List<String> chunks, int topN, Long modelId) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        // 限制候选数量
        List<String> candidates = chunks.size() > MAX_CANDIDATES
            ? chunks.subList(0, MAX_CANDIDATES) : chunks;

        // 获取 ChatModel（复用 ChatModelFactory 缓存）
        ChatModel chatModel = getRerankChatModel(modelId);

        // 并行打分
        List<ScoredChunk> scored = parallelScore(chatModel, query, candidates);

        // 按分数降序排列，取 Top N
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream()
            .limit(topN)
            .map(s -> s.content)
            .toList();
    }

    /**
     * 并行打分（使用 aiExecutor 线程池，带超时控制）
     *
     * @param chatModel  用于打分的 ChatModel 实例
     * @param query      查询文本
     * @param candidates 候选片段列表
     * @return 按原始顺序排列的已打分片段列表
     */
    private List<ScoredChunk> parallelScore(ChatModel chatModel, String query, List<String> candidates) {
        List<CompletableFuture<ScoredChunk>> futures = new ArrayList<>();
        AtomicInteger indexCounter = new AtomicInteger(0);

        for (String chunk : candidates) {
            int index = indexCounter.getAndIncrement();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String prompt = buildRerankPrompt(query, chunk);
                    String response = chatModel.chat(prompt);
                    double score = parseScore(response);
                    log.debug("[Rerank] chunk[{}] score={}", index, score);
                    return new ScoredChunk(chunk, score, index);
                } catch (Exception e) {
                    log.warn("[Rerank] chunk[{}] 打分失败: {}", index, e.getMessage());
                    return new ScoredChunk(chunk, 0.0, index);
                }
            }, aiExecutor).orTimeout(SCORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(e -> {
                    log.warn("[Rerank] chunk[{}] 超时或异常: {}", index, e.getMessage());
                    return new ScoredChunk(candidates.get(index), 0.0, index);
                }));
        }

        // 等待所有打分完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
            .map(f -> f.getNow(new ScoredChunk("", 0.0, -1)))
            .filter(s -> s.originalIndex >= 0)
            .toList();
    }

    /**
     * 获取重排序用的 ChatModel
     * <p>优先使用指定的 rerank 模型，否则使用默认 CHAT 模型</p>
     * <p>复用 ChatModelFactory 的缓存机制，避免重复构建</p>
     *
     * @param modelId 指定的模型ID，可为 null
     * @return ChatModel 实例
     * @throws IllegalStateException 未找到任何可用模型时抛出
     */
    private ChatModel getRerankChatModel(Long modelId) {
        if (modelId != null) {
            try {
                return chatModelFactory.getChatModel(modelId);
            } catch (Exception e) {
                log.warn("[Rerank] 指定的 Rerank 模型不可用 (modelId={})，回退到默认模型: {}", modelId, e.getMessage());
            }
        }
        // 回退到默认模型
        AiModel defaultModel = aiModelService.selectDefaultModel();
        if (defaultModel != null) {
            return chatModelFactory.getChatModel(defaultModel.getId());
        }
        throw new IllegalStateException("[Rerank] 未找到可用的 ChatModel，请配置默认模型");
    }

    /**
     * 构建重排序提示词
     * <p>将查询和文档片段拼接，要求 LLM 返回 0-10 的相关性评分</p>
     *
     * @param query 查询文本
     * @param chunk 文档片段（超过 {@link #MAX_CHUNK_LENGTH} 会截断）
     * @return 格式化后的提示词
     */
    private String buildRerankPrompt(String query, String chunk) {
        String truncatedChunk = chunk.length() > MAX_CHUNK_LENGTH
            ? chunk.substring(0, MAX_CHUNK_LENGTH) : chunk;
        return String.format("""
            Query: %s
            Document: %s

            On a scale of 0 to 10, how relevant is the document to the query?
            Reply with only a number (0-10), nothing else.
            Score:""", query, truncatedChunk);
    }

    /**
     * 解析 LLM 返回的分数
     * <p>从 LLM 响应中提取第一个数字，归一化到 0-1 区间</p>
     *
     * @param response LLM 返回的文本
     * @return 归一化后的分数（0-1），解析失败返回 0
     */
    private double parseScore(String response) {
        if (response == null || response.isEmpty()) return 0;
        try {
            String cleaned = response.trim().replaceAll("[^0-9.]", " ").trim();
            if (cleaned.isEmpty()) return 0;
            String[] parts = cleaned.split("\\s+");
            double score = Double.parseDouble(parts[0]);
            return Math.max(0, Math.min(10, score)) / 10.0; // 归一化到 0-1
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 已打分的片段记录
     *
     * @param content        片段文本内容
     * @param score          归一化分数（0-1）
     * @param originalIndex  在候选列表中的原始索引
     */
    private record ScoredChunk(String content, double score, int originalIndex) {}
}
