package com.nexus.ai.service.rag.embedding;

import com.nexus.common.ai.domain.entity.AiModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 嵌入模型构建工厂
 * <p>自动收集所有 {@link EmbeddingProvider} 实现，按 providerType 路由到对应厂商</p>
 * <p>扩展新厂商只需新增 EmbeddingProvider 实现类并标注 {@code @Component}，无需修改此工厂</p>
 * <p>未知厂商类型自动回退到 OPENAI 兼容接口</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Component
public class EmbeddingProviderFactory {

    /** 厂商类型 → 嵌入模型构建策略 注册表（构造时从 Spring 收集并注册）。 */
    private final Map<String, EmbeddingProvider> providers;
    /** Embedding 监听器列表（来自 Spring 容器，可能为空）。 */
    private final List<EmbeddingModelListener> listeners;

    public EmbeddingProviderFactory(List<EmbeddingProvider> providerList,
                                    List<EmbeddingModelListener> listeners) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(
                p -> p.getProviderType().toUpperCase(),
                Function.identity()
            ));
        this.listeners = listeners != null ? listeners : Collections.emptyList();
        log.info("[EmbeddingProviderFactory] 已注册 Embedding Provider: {}, listeners={}",
            providers.keySet(), this.listeners.size());
    }

    /**
     * 根据模型配置构建 EmbeddingModel
     * <p>按 {@code config.providerType} 路由到对应 Provider，未知类型回退到 OPENAI 兼容接口</p>
     *
     * @param config 模型配置（包含 providerType、apiHost、apiKey、modelName 等）
     * @return EmbeddingModel 实例
     * @throws IllegalArgumentException 未找到任何可用 Provider 时抛出
     */
    public EmbeddingModel build(AiModel config) {
        String type = config.getProviderType() != null
            ? config.getProviderType().toUpperCase() : "OPENAI";

        EmbeddingProvider provider = providers.get(type);
        if (provider != null) {
            return provider.build(config, listeners);
        }

        // 回退到 OpenAI 兼容接口
        log.warn("[EmbeddingProviderFactory] 未找到 Provider: {}，回退到 OPENAI 兼容接口", type);
        EmbeddingProvider fallback = providers.get("OPENAI");
        if (fallback != null) {
            return fallback.build(config, listeners);
        }

        throw new IllegalArgumentException("未找到可用的 Embedding Provider: " + type);
    }
}
