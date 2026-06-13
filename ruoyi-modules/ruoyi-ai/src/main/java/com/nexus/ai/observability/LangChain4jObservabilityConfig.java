package com.nexus.ai.observability;

import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiTokenUsageService;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LangChain4j 可观测性配置
 * <p>注册 ChatModel/ToolExecuted 监听器到 LangChain4j 框架</p>
 * <p>{@link ChatModelListener} 通过 Spring Bean 注入到 {@code ChatModelFactory}，再传入 OpenAI 模型构建器</p>
 * <p>{@link AiToolExecutedListener} 通过 {@code AiServiceListenerRegistrar} 全局注册</p>
 * <p>注意：OpenAiEmbeddingModel 在 langchain4j 1.13.0 中不支持 {@code listeners()} 注入，
 * {@link AiEmbeddingModelListener} 暂未注册，待后续版本支持后启用</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class LangChain4jObservabilityConfig {

    /**
     * 注册 AI Service 级别监听器
     * <p>通过 {@code AiServiceListenerRegistrar} 全局注册 {@link AiToolExecutedListener}，
     * 使所有 AI Service 的工具调用自动触发监听</p>
     */
    @PostConstruct
    public void registerAiServiceListeners() {
        try {
            var registrar = dev.langchain4j.observability.api.AiServiceListenerRegistrar.newInstance();
            registrar.register(new AiToolExecutedListener());
            log.info("[Observability] AI Service 监听器注册完成: AiToolExecutedListener");
        } catch (Exception e) {
            log.warn("[Observability] AI Service 监听器注册失败（可能 langchain4j-agentic 版本不支持）: {}", e.getMessage());
        }
    }

    /**
     * ChatModel 监听器 Bean（持久化版）
     * <p>注入到 {@code ChatModelFactory}，在构建 OpenAI 模型时传入 {@code .listeners()}</p>
     * <p>在 LLM 响应回调中更新 ai_message 的 token 明细和 ai_token_usage 日统计</p>
     *
     * @param aiMessageMapper     消息 Mapper
     * @param aiTokenUsageService Token 用量服务
     * @return {@link AiChatModelListener} 实例
     */
    @Bean
    public ChatModelListener chatModelListener(AiMessageMapper aiMessageMapper,
                                               IAiTokenUsageService aiTokenUsageService) {
        return new AiChatModelListener(aiMessageMapper, aiTokenUsageService);
    }

    /**
     * ChatModel 监听器列表 Bean
     * <p>供 {@code ChatModelFactory} 通过 {@code List<ChatModelListener>} 注入</p>
     *
     * @param chatModelListener 单例 ChatModel 监听器
     * @return 包含单个监听器的列表
     */
    @Bean
    public List<ChatModelListener> chatModelListeners(ChatModelListener chatModelListener) {
        return List.of(chatModelListener);
    }
}
