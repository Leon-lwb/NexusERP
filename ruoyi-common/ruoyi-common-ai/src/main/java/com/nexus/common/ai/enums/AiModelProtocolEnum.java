package com.nexus.common.ai.enums;

import lombok.Getter;

import java.util.Locale;

/**
 * AI 模型 API 协议枚举
 * <p>对应 ai_model.api_protocol 字段（varchar(64)），值与字典 ai_model_api_protocol 一致。
 * 对话路由按协议选择适配器，NULL 视为未指定（由厂商类型推断）。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Getter
public enum AiModelProtocolEnum {

    /** OpenAI Chat Completions 协议（/v1/chat/completions，业界通用） */
    OPENAI_CHAT_COMPLETIONS("OPENAI_CHAT_COMPLETIONS", "OpenAI Chat Completions 协议"),
    /** OpenAI Responses 协议（/v1/responses，新一代接口） */
    OPENAI_RESPONSES("OPENAI_RESPONSES", "OpenAI Responses 协议"),
    /** Anthropic Messages 协议（/v1/messages） */
    ANTHROPIC_MESSAGES("ANTHROPIC_MESSAGES", "Anthropic Messages 协议"),
    /** Gemini generateContent 协议 */
    GEMINI_GENERATE_CONTENT("GEMINI_GENERATE_CONTENT", "Gemini generateContent 协议"),
    /** 阿里云百灵原生协议 */
    DASHSCOPE_NATIVE("DASHSCOPE_NATIVE", "阿里云百灵原生协议"),
    /** 智谱原生协议 */
    ZHIPU_NATIVE("ZHIPU_NATIVE", "智谱原生协议"),
    /** Ollama 原生协议（/api/chat） */
    OLLAMA_NATIVE("OLLAMA_NATIVE", "Ollama 原生协议");

    private final String code;
    private final String description;

    AiModelProtocolEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * code 是否为合法协议值（NULL/空白/未知均返回 false，NULL 语义为未指定协议）
     */
    public static boolean isKnown(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (AiModelProtocolEnum protocol : values()) {
            if (protocol.code.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
