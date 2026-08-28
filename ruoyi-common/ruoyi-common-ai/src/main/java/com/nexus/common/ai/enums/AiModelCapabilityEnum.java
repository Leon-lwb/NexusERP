package com.nexus.common.ai.enums;

import com.nexus.common.core.utils.StringUtils;
import lombok.Getter;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * AI 模型能力枚举
 * <p>对应 ai_model.input_capabilities / output_capabilities 字段（varchar(128)），
 * 多能力以英文逗号拼接存储，顺序固定为本枚举声明顺序，值与字典 ai_model_capability 一致。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Getter
public enum AiModelCapabilityEnum {

    /** 文本输入/输出 */
    TEXT("TEXT", "文本"),
    /** 图像输入/输出 */
    IMAGE("IMAGE", "图像"),
    /** 视频输入/输出 */
    VIDEO("VIDEO", "视频"),
    /** 音频输入/输出 */
    AUDIO("AUDIO", "音频"),
    /** 文件输入 */
    FILE("FILE", "文件"),
    /** 向量输出（Embedding 模型） */
    VECTOR("VECTOR", "向量"),
    /** 重排序输出（Rerank 模型） */
    RANKING("RANKING", "重排序");

    private final String code;
    private final String description;

    AiModelCapabilityEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 规范化能力列表
     * <p>逐项校验合法性（未知能力抛出 IllegalArgumentException），去重后
     * 按枚举声明顺序排序，以英文逗号拼接返回，如 "TEXT,IMAGE"。</p>
     *
     * @param capabilities 原始能力列表（元素允许大小写混写，不允许空白）
     * @return 规范化后的能力串
     */
    public static String canonicalize(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("能力列表不能为空");
        }
        EnumSet<AiModelCapabilityEnum> parsed = EnumSet.noneOf(AiModelCapabilityEnum.class);
        for (String capability : capabilities) {
            parsed.add(fromCode(capability));
        }
        return parsed.stream()
            .map(AiModelCapabilityEnum::getCode)
            .reduce((l, r) -> l + "," + r)
            .orElseThrow();
    }

    /**
     * 规范化逗号分隔的能力串（单值等价于单元素列表）
     * <p>未提供（null/空白）返回 null，语义为"未填写，不修改/不校验"；
     * 其余规则同 {@link #canonicalize(List)}。</p>
     *
     * @param csv 逗号分隔能力串（元素允许大小写混写）
     * @return 规范化后的能力串；未提供时返回 null
     */
    public static String canonicalize(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        return canonicalize(StringUtils.splitList(csv));
    }

    /**
     * 解析逗号分隔的能力串为枚举集合
     * <p>未提供（null/空白）返回空集合（legacy 数据能力列可能为空的场景）；
     * 非法元素抛出 IllegalArgumentException。</p>
     *
     * @param csv 逗号分隔能力串
     * @return 能力枚举集合（去重）
     */
    public static EnumSet<AiModelCapabilityEnum> parse(String csv) {
        EnumSet<AiModelCapabilityEnum> parsed = EnumSet.noneOf(AiModelCapabilityEnum.class);
        if (csv == null || csv.isBlank()) {
            return parsed;
        }
        for (String capability : StringUtils.splitList(csv)) {
            parsed.add(fromCode(capability));
        }
        return parsed;
    }

    /**
     * 模型类型的默认输入能力模板
     * <p>TEXT→TEXT、VISION→TEXT,IMAGE、AUDIO→TEXT、EMBEDDING→TEXT、RERANK→TEXT、IMAGE→TEXT</p>
     *
     * @param modelType 模型类型（字典 ai_model_type）
     * @return 默认输入能力串
     */
    public static String defaultInput(String modelType) {
        return template(modelType)[0];
    }

    /**
     * 模型类型的默认输出能力模板
     * <p>TEXT→TEXT、VISION→TEXT、AUDIO→TEXT、EMBEDDING→VECTOR、RERANK→RANKING、IMAGE→IMAGE</p>
     *
     * @param modelType 模型类型（字典 ai_model_type）
     * @return 默认输出能力串
     */
    public static String defaultOutput(String modelType) {
        return template(modelType)[1];
    }

    /**
     * 各模型类型的默认能力模板（输入能力在前，输出能力在后）
     */
    private static String[] template(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        String type = normalize(modelType);
        return switch (type) {
            case "TEXT" -> new String[]{"TEXT", "TEXT"};
            case "VISION" -> new String[]{"TEXT,IMAGE", "TEXT"};
            case "AUDIO" -> new String[]{"TEXT", "TEXT"};
            case "EMBEDDING" -> new String[]{"TEXT", "VECTOR"};
            case "RERANK" -> new String[]{"TEXT", "RANKING"};
            case "IMAGE" -> new String[]{"TEXT", "IMAGE"};
            default -> throw new IllegalArgumentException("未知模型类型: " + modelType);
        };
    }

    /**
     * code 是否为合法能力值
     */
    public static boolean isKnown(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = normalize(code);
        for (AiModelCapabilityEnum capability : values()) {
            if (capability.code.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析能力 code 为枚举，未知值抛出 IllegalArgumentException
     */
    private static AiModelCapabilityEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("能力值不能为空");
        }
        String normalized = normalize(code);
        for (AiModelCapabilityEnum capability : values()) {
            if (capability.code.equals(normalized)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("未知模型能力: " + code);
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
