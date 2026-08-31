package com.nexus.common.ai.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;

/**
 * AI 模型类型枚举
 * <p>对应 ai_model.model_type 字段（varchar(32)），值与字典 ai_model_type 一致。
 * 前端模型管理页/选择组件按本字典渲染，后端行为分支统一取本枚举常量。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Getter
public enum AiModelTypeEnum {

    /** 文本模型（对话/推理/生成，原 CHAT） */
    TEXT("TEXT", "文本模型"),
    /** 视觉模型（多模态图像理解，可对话） */
    VISION("VISION", "视觉模型"),
    /** 语音模型（ASR/TTS，预留） */
    AUDIO("AUDIO", "语音模型"),
    /** 向量模型 */
    EMBEDDING("EMBEDDING", "向量模型"),
    /** 重排模型 */
    RERANK("RERANK", "重排模型"),
    /** 图像生成模型（文生图） */
    IMAGE("IMAGE", "图像生成模型");

    private final String code;
    private final String description;

    AiModelTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 是否支持推理（Thinking）模式
     * <p>仅文本对话类模型支持 returnThinking 推理 token 流</p>
     */
    public static boolean supportsThinking(String modelType) {
        return isChat(modelType);
    }

    /**
     * 是否为文本对话模型
     */
    public static boolean isChat(String modelType) {
        return TEXT.code.equalsIgnoreCase(modelType);
    }

    /**
     * 是否为视觉模型（多模态图像理解）
     */
    public static boolean isVision(String modelType) {
        return VISION.code.equalsIgnoreCase(modelType);
    }

    /**
     * 是否可用于对话（文本或视觉模型均可作为 ChatModel 使用，视觉模型是文本能力的超集）
     */
    public static boolean chatCompatible(String modelType) {
        return isChat(modelType) || isVision(modelType);
    }

    /**
     * 是否为图像生成模型
     */
    public static boolean isImage(String modelType) {
        return IMAGE.code.equalsIgnoreCase(modelType);
    }

    /**
     * code 是否为合法类型值
     */
    public static boolean isValid(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
            .anyMatch(t -> t.code.equalsIgnoreCase(modelType.trim().toUpperCase(Locale.ROOT)));
    }
}
