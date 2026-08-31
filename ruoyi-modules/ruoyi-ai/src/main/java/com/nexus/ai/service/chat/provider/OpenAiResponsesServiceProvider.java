package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OpenAI Responses 协议策略（OPENAI_RESPONSES，/v1/responses 新一代接口）
 * <p>langchain4j-open-ai 1.18 自带 OpenAiResponsesChatModel / OpenAiResponsesStreamingChatModel，
 * 适用 OpenAI 官方 Responses 接口及兼容该协议的中转站（如 OpenCode Go 的 Responses 端点），
 * baseUrl 取 ai_provider.api_host（与 Chat Completions 协议同源）。</p>
 * <p>构建参数对齐 {@link AbstractOpenAiCompatibleProvider}：请求级 maxOutputTokens 封顶、
 * maxTokens 防御性裁剪、Token 用量 listeners、180s 读超时（Responses builder 无 timeout 项，
 * 经 JdkHttpClientBuilder.readTimeout 显式注入，SDK 默认为 null 即永不超时）。
 * Responses builder 无 returnThinking 项，推理流开关对该协议无额外效果。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Component
public class OpenAiResponsesServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProtocol() {
        return AiProviderPolicyRegistry.OPENAI_RESPONSES;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        // 请求级 maxOutputTokens 优先（与 Chat Completions 协议一致的封顶语义）
        Integer maxTokens = request != null && request.getMaxOutputTokens() != null
            ? request.getMaxOutputTokens() : config.getMaxTokens();
        return OpenAiResponsesStreamingChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxOutputTokens(sanitizeMaxTokens(maxTokens))
            // Responses builder 无 timeout() 项，底层 JdkHttpClient 默认 readTimeout=null
            // （永不超时）——必须显式注入 httpClientBuilder 补上 180s 读超时，
            // 与 AbstractOpenAiCompatibleProvider 的中转站长输出防挂死策略对齐
            .httpClientBuilder(new JdkHttpClientBuilder().readTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS)))
            .listeners(chatModelListeners)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return OpenAiResponsesChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxOutputTokens(sanitizeMaxTokens(config.getMaxTokens()))
            .httpClientBuilder(new JdkHttpClientBuilder().readTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS)))
            .listeners(chatModelListeners)
            .build();
    }
}
