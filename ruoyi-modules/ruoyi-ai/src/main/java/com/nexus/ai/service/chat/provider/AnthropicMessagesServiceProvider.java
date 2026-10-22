package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Anthropic Messages 协议策略（ANTHROPIC_MESSAGES，/v1/messages）
 * <p>langchain4j-anthropic 的 AnthropicChatModel / AnthropicStreamingChatModel。
 * 支持自定义 baseUrl（ai_provider.api_host）：Anthropic 官方端点与
 * OpenCode Go 等 Anthropic 兼容中转端点同用本策略。</p>
 * <p><b>apiHost 填写注意</b>：SDK 只在 baseUrl 后拼 "messages"（默认 base 为
 * https://api.anthropic.com/v1/），因此 api_host 必须自带 /v1 版本段
 * （如 https://api.anthropic.com/v1 或网关的等价含版本路径）；不做自动补 /
 * v1——自定义网关的 base 可能已含版本段，自动拼接会双拼出错。</p>
 * <p>apiHost/apiKey 与其他协议同源：由 AiModelServiceImpl.fillProviderInfo 从
 * provider_id 关联填充（apiKey 经 Jasypt 解密，失败回退明文）。maxTokens 未配置时
 * 本策略兜底 4096（SDK 默认 1024 对长输出偏低）。思考流：returnThinking(true) 仅在
 * 模型实际生成思考块时回流（thinkingType/thinkingBudgetTokens 默认不配置即不生成，
 * 不产生额外成本），与 enableThinking 开关联动见 {@link #supportsThinking()}。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class AnthropicMessagesServiceProvider implements ChatServiceProvider {

    /** Spring 收集的 ChatModelListener 列表（Token 用量采集） */
    private final List<ChatModelListener> chatModelListeners;

    /** HTTP 读超时（对齐 OpenAI 兼容协议的 180s，给推理模型足够回流时间） */
    private static final long HTTP_TIMEOUT_SECONDS = 180;

    /** maxTokens 未配置时的兜底输出上限（SDK 默认 1024 对长输出偏低） */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Override
    public String getProtocol() {
        return AiProviderPolicyRegistry.ANTHROPIC_MESSAGES;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        Integer maxTokens = request != null && request.getMaxOutputTokens() != null
            ? request.getMaxOutputTokens() : config.getMaxTokens();
        return AnthropicStreamingChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxTokens(sanitizeMaxTokens(maxTokens))
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            // 思考块仅在模型实际生成时回流（不配置 thinkingType/budget 即不生成，无额外成本）
            .returnThinking(true)
            .listeners(chatModelListeners)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return AnthropicChatModel.builder()
                .baseUrl(normalizeBaseUrl(config.getApiHost()))
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .maxRetries(0)
                .temperature(temperature)
                .maxTokens(sanitizeMaxTokens(config.getMaxTokens()))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .returnThinking(true)
                .listeners(chatModelListeners)
                .build();
    }

    /**
     * 去除 baseUrl 末尾的 /，避免拼接 /v1/messages 时出现双斜杠（与 OpenAI 兼容基类同规则）
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("API 请求地址(apiHost)不能为空");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 规范化 maxTokens：null/≤0 时兜底 4096（SDK 默认 1024 对长输出偏低）；超 393216 截断防 400
     */
    private Integer sanitizeMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens <= 0) {
            return DEFAULT_MAX_TOKENS;
        }
        return Math.min(maxTokens, 393216);
    }
}
