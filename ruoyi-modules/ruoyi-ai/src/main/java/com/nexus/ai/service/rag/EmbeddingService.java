package com.nexus.ai.service.rag;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量化服务
 * <p>根据数据库配置动态构建 EmbeddingModel，支持多厂商路由和缓存</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final IAiModelService aiModelService;

    /** Embedding 模型缓存（key = modelId） */
    private final ConcurrentHashMap<Long, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    /**
     * 获取默认 Embedding 模型
     *
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel getDefaultEmbeddingModel() {
        AiModel config = aiModelService.selectDefaultEmbeddingModel();
        if (config == null) {
            throw new IllegalStateException("未配置 Embedding 模型，请在模型管理中添加 EMBEDDING 类型模型");
        }
        return getEmbeddingModel(config.getId());
    }

    /**
     * 获取指定 Embedding 模型（带缓存）
     *
     * @param modelId 模型ID
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel getEmbeddingModel(Long modelId) {
        return embeddingModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            return buildEmbeddingModel(config);
        });
    }

    /**
     * 清除指定模型缓存
     *
     * @param modelId 模型ID
     */
    public void evictCache(Long modelId) {
        embeddingModelCache.remove(modelId);
    }

    /**
     * 构建 Embedding 模型（按 providerType 路由）
     *
     * @param config 模型配置
     * @return EmbeddingModel 实例
     */
    private EmbeddingModel buildEmbeddingModel(AiModel config) {
        String providerType = config.getProviderType();
        log.info("[EmbeddingService] 构建 EmbeddingModel: providerType={}, modelName={}", providerType, config.getModelName());

        if (providerType == null) {
            providerType = "OPENAI";
        }

        // 目前所有厂商均走 OpenAI 兼容接口
        return switch (providerType.toUpperCase()) {
            case "OLLAMA" -> dev.langchain4j.model.ollama.OllamaEmbeddingModel.builder()
                .baseUrl(config.getApiHost())
                .modelName(config.getModelName())
                .build();
            case "OPENAI", "DASHSCOPE", "ZHIPU" -> OpenAiEmbeddingModel.builder()
                .baseUrl(config.getApiHost())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
            default -> {
                log.warn("[EmbeddingService] 未知的 providerType: {}，回退到 OpenAI 兼容接口", providerType);
                yield OpenAiEmbeddingModel.builder()
                    .baseUrl(config.getApiHost())
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .build();
            }
        };
    }
}
