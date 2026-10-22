package com.nexus.ai.service.chat.provider;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI Chat Completions 协议构建基类
 * <p>覆盖全部 OpenAI 兼容厂商（OpenAI/DeepSeek/MiniMax/小米/方舟/中转站等），
 * 厂商差异只体现在模型的 apiHost/apiKey/modelName 配置上，协议实现唯一：
 * {@link OpenAiServiceProvider}（协议 OPENAI_CHAT_COMPLETIONS）。</p>
 * <p>支持 returnThinking 推理 token 流（{@link #supportsThinking()} 默认 true）</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
public abstract class AbstractOpenAiCompatibleProvider implements ChatServiceProvider {

    /**
     * Spring 收集的 ChatModelListener 列表（含 AiChatModelListener，用于 Token 用量采集）
     */
    @Autowired
    protected List<ChatModelListener> chatModelListeners;

    /**
     * HTTP 读超时（秒）。
     * <p>SDK 默认 readTimeout=60s，对中转站长输出场景偏短（idle 60s 触发 HttpTimeoutException）。
     * 提到 180s 给上游足够时间回流首 token；流式启用后中转站持续回字节，几乎不会触达此上限。</p>
     */
    protected static final int HTTP_TIMEOUT_SECONDS = 180;

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        boolean thinking = Boolean.TRUE.equals(request.getEnableThinking()) && supportsThinking();
        double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        // 请求级 maxOutputTokens 优先（表单生成等场景按调用封顶输出体量），否则用模型配置
        Integer maxTokens = request != null && request.getMaxOutputTokens() != null
            ? request.getMaxOutputTokens() : config.getMaxTokens();
        return OpenAiStreamingChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxTokens(sanitizeMaxTokens(maxTokens))
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .listeners(chatModelListeners)
            .returnThinking(thinking)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return OpenAiChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxTokens(sanitizeMaxTokens(config.getMaxTokens()))
            // 与流式构建器同一超时纪律：默认 60s 对图片复核（base64 多图入参）等
            // 长交互偏短，统一提到 180s，避免视觉/复核链路无谓超时
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .listeners(chatModelListeners)
            .build();
    }

    /**
     * 规范化 maxTokens，规避上游 {@code Invalid max_tokens value}（中转站常见上下界 [1, 393216]）。
     * <p>null/≤0 时不传该字段（langchain4j 对 null maxTokens 不下发到请求体，交由上游默认）；
     * 超出常规上界 393216 时截断，避免 400。仅作防御性裁剪，不影响正常配置。</p>
     *
     * @param maxTokens 库配置的最大输出 Token
     * @return 规范化后的值，null 表示不下发
     */
    protected Integer sanitizeMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens <= 0) {
            return null;
        }
        // 中转站普遍上限；官方 OpenAI 上限更大但 393216 对"单次回复"已绰绰有余
        if (maxTokens > 393216) {
            return 393216;
        }
        return maxTokens;
    }

    /**
     * 去除 baseUrl 末尾的 /，避免拼接路径时出现双斜杠
     *
     * @param baseUrl 原始地址
     * @return 规范化后的地址
     */
    protected String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("API 请求地址(apiHost)不能为空");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
