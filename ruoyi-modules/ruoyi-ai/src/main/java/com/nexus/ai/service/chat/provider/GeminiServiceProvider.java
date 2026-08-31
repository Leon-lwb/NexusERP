package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Gemini generateContent 协议策略（GEMINI_GENERATE_CONTENT）
 * <p>langchain4j-google-ai-gemini 的 GoogleAiGeminiChatModel / GoogleAiGeminiStreamingChatModel。
 * 官方 Google AI Studio 端点可不填 api_host（SDK 默认 generativelanguage.googleapis.com）；
 * 配置了 api_host（如第三方 Gemini 兼容网关）则透传。apiKey 为 Gemini API Key，
 * 与其他协议同源由 AiModelServiceImpl.fillProviderInfo 填充（Jasypt 解密，失败回退明文）。</p>
 * <p>思考流：returnThinking(true) 仅在模型实际生成 thoughts 时回流（thinkingConfig
 * 默认不配置即不主动生成思考，不产生额外成本）。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class GeminiServiceProvider implements ChatServiceProvider {

    /** Spring 收集的 ChatModelListener 列表（Token 用量采集） */
    private final List<ChatModelListener> chatModelListeners;

    /** HTTP 读超时（对齐其他协议的 180s） */
    private static final long HTTP_TIMEOUT_SECONDS = 180;

    @Override
    public String getProtocol() {
        return AiProviderPolicyRegistry.GEMINI_GENERATE_CONTENT;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        Integer maxTokens = request != null && request.getMaxOutputTokens() != null
            ? request.getMaxOutputTokens() : config.getMaxTokens();
        GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder =
            GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(temperature)
                .maxOutputTokens(sanitizeMaxTokens(maxTokens))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                // thoughts 仅在模型实际生成时回流（不配置 thinkingConfig 即不生成，无额外成本）
                .returnThinking(true)
                .listeners(chatModelListeners);
        if (config.getApiHost() != null && !config.getApiHost().isBlank()) {
            builder.baseUrl(normalizeBaseUrl(config.getApiHost()));
        }
        return builder.build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxOutputTokens(sanitizeMaxTokens(config.getMaxTokens()))
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .returnThinking(true)
            .listeners(chatModelListeners);
        if (config.getApiHost() != null && !config.getApiHost().isBlank()) {
            builder.baseUrl(normalizeBaseUrl(config.getApiHost()));
        }
        return builder.build();
    }

    /**
     * 去除 baseUrl 末尾的 /（与 OpenAI 兼容基类同规则）
     */
    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 规范化 maxTokens（null/≤0 不下发交由 SDK 默认；超 393216 截断防 400）
     */
    private Integer sanitizeMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens <= 0) {
            return null;
        }
        return Math.min(maxTokens, 393216);
    }
}
