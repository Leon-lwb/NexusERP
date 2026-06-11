package com.nexus.ai.service.rag;

import com.nexus.ai.service.rag.embedding.EmbeddingProviderFactory;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量化服务
 * <p>根据数据库配置动态构建 EmbeddingModel，通过 {@link EmbeddingProviderFactory} 策略模式路由到不同厂商</p>
 * <p>支持 OpenAI 兼容协议（OpenAI/DashScope/ZhiPu）和 Ollama，扩展新厂商只需新增 EmbeddingProvider 实现</p>
 * <p>模型实例按 modelId 缓存，模型配置变更时需调用 {@link #evictCache(Long)} 清除缓存</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final IAiModelService aiModelService;
    private final EmbeddingProviderFactory embeddingProviderFactory;

    /** Embedding 模型缓存（key = modelId） */
    private final ConcurrentHashMap<Long, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    /**
     * 获取默认 Embedding 模型
     * <p>从数据库查询默认 EMBEDDING 类型模型，构建并缓存 EmbeddingModel 实例</p>
     *
     * @return EmbeddingModel 实例
     * @throws IllegalStateException 未配置默认 Embedding 模型时抛出
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
     * <p>首次获取时通过 {@link EmbeddingProviderFactory} 构建，后续从缓存读取</p>
     *
     * @param modelId 模型ID
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel getEmbeddingModel(Long modelId) {
        return embeddingModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            return embeddingProviderFactory.build(config);
        });
    }

    /**
     * 根据模型名称获取 Embedding 模型
     * <p>按 modelName 查询数据库配置，构建 EmbeddingModel 实例并缓存</p>
     *
     * @param modelName 模型名称
     * @return EmbeddingModel 实例
     * @throws IllegalStateException 模型不存在或类型不匹配时抛出
     */
    public EmbeddingModel getEmbeddingModel(String modelName) {
        AiModel config = aiModelService.selectModelByName(modelName);
        if (config == null) {
            throw new IllegalStateException("Embedding 模型 '" + modelName + "' 不存在，请检查模型配置");
        }
        if (!"EMBEDDING".equals(config.getModelType())) {
            throw new IllegalStateException("模型 '" + modelName + "' 类型为 " + config.getModelType() + "，不是 EMBEDDING 类型");
        }
        return embeddingModelCache.computeIfAbsent(config.getId(), id -> embeddingProviderFactory.build(config));
    }

    /**
     * 清除指定模型缓存
     * <p>模型配置变更时调用，下次获取将重新构建</p>
     *
     * @param modelId 模型ID
     */
    public void evictCache(Long modelId) {
        embeddingModelCache.remove(modelId);
    }
}
