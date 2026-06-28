package com.nexus.common.ai.enums;

import lombok.Getter;

/**
 * AI 模型类型枚举
 * <p>对应 ai_model.model_type 字段（varchar(32)）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Getter
public enum AiModelTypeEnum {

    /** 对话模型 */
    CHAT("CHAT", "对话模型"),
    /** 向量模型 */
    EMBEDDING("EMBEDDING", "向量模型"),
    /** 重排模型 */
    RERANK("RERANK", "重排模型"),
    /** 图像生成模型（S2-C 文生图） */
    IMAGE("IMAGE", "图像生成模型");

    private final String code;
    private final String description;

    AiModelTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 是否支持推理（Thinking）模式
     * <p>仅 CHAT 模型支持 returnThinking 推理 token 流</p>
     *
     * @param modelType 模型类型字符串
     * @return true 表示支持推理
     */
    public static boolean supportsThinking(String modelType) {
        return CHAT.code.equalsIgnoreCase(modelType);
    }

    public static boolean isChat(String modelType) {
        return CHAT.code.equalsIgnoreCase(modelType);
    }

    public static boolean isImage(String modelType) {
        return IMAGE.code.equalsIgnoreCase(modelType);
    }
}
