package com.nexus.ai.service.model;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.enums.AiModelCapabilityEnum;
import com.nexus.common.ai.enums.AiModelTypeEnum;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Locale;

/**
 * 模型能力选择器（静态工具，运行时消费者的统一能力闸门）
 * <p>按「模型类型 + 输入/输出能力码」判定模型能否承担某类运行时职责，
 * 替代消费者各自按厂商类型（如 OpenAI 兼容）硬编码的判断。每个判定都要求
 * <b>类型与能力同时满足</b>：</p>
 * <ul>
 *   <li>{@link #supportsChat}：TEXT/VISION 类型 + TEXT 输入 + TEXT 输出（视觉模型是文本能力超集）</li>
 *   <li>{@link #supportsVisionReview}：VISION 类型 + TEXT,IMAGE 输入 + TEXT 输出</li>
 *   <li>{@link #supportsEmbedding}：EMBEDDING 类型 + TEXT 输入 + VECTOR 输出</li>
 *   <li>{@link #supportsRerank}：RERANK 类型 + TEXT 输入 + RANKING 输出</li>
 *   <li>{@link #supportsImageGeneration}：IMAGE 类型 + TEXT 输入 + IMAGE 输出</li>
 * </ul>
 * <p>健壮性约定：null 模型、能力列 null/空白、能力码非法（存量脏数据）一律返回 false
 * （按能力不足处理），绝不抛异常——运行时链路据此降级或给出可操作的配置提示，
 * 而不是被脏数据打崩。</p>
 *
 * @author nexus-school
 * @since 2.1.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiModelCapabilitySelection {

    /**
     * 模型能否用于对话（文本或视觉模型均可，视觉模型是文本能力的超集）
     * <p>消费方：通用对话/纯文本复核降级链路。</p>
     */
    public static boolean supportsChat(AiModel model) {
        if (model == null) {
            return false;
        }
        String type = normalizeType(model.getModelType());
        if (!AiModelTypeEnum.TEXT.getCode().equals(type) && !AiModelTypeEnum.VISION.getCode().equals(type)) {
            return false;
        }
        return capabilities(model.getInputCapabilities()).contains(AiModelCapabilityEnum.TEXT)
            && capabilities(model.getOutputCapabilities()).contains(AiModelCapabilityEnum.TEXT);
    }

    /**
     * 模型能否用于视觉复核（图片 + 提示词 → 文本结论）
     * <p>要求 VISION 类型且输入同时含 TEXT 与 IMAGE（提示词 + 图片）、输出含 TEXT。</p>
     */
    public static boolean supportsVisionReview(AiModel model) {
        if (model == null || !AiModelTypeEnum.VISION.getCode().equals(normalizeType(model.getModelType()))) {
            return false;
        }
        EnumSet<AiModelCapabilityEnum> input = capabilities(model.getInputCapabilities());
        return input.contains(AiModelCapabilityEnum.TEXT)
            && input.contains(AiModelCapabilityEnum.IMAGE)
            && capabilities(model.getOutputCapabilities()).contains(AiModelCapabilityEnum.TEXT);
    }

    /**
     * 模型能否用于向量化
     * <p>EMBEDDING 类型 + TEXT 输入 + VECTOR 输出。</p>
     */
    public static boolean supportsEmbedding(AiModel model) {
        return typedSupports(model, AiModelTypeEnum.EMBEDDING,
            AiModelCapabilityEnum.TEXT, AiModelCapabilityEnum.VECTOR);
    }

    /**
     * 模型能否用于重排
     * <p>RERANK 类型 + TEXT 输入 + RANKING 输出。</p>
     */
    public static boolean supportsRerank(AiModel model) {
        return typedSupports(model, AiModelTypeEnum.RERANK,
            AiModelCapabilityEnum.TEXT, AiModelCapabilityEnum.RANKING);
    }

    /**
     * 模型能否用于文生图
     * <p>IMAGE 类型 + TEXT 输入（提示词）+ IMAGE 输出（生成图）。</p>
     */
    public static boolean supportsImageGeneration(AiModel model) {
        return typedSupports(model, AiModelTypeEnum.IMAGE,
            AiModelCapabilityEnum.TEXT, AiModelCapabilityEnum.IMAGE);
    }

    /**
     * 类型完全匹配 + 指定输入/输出能力均在集合内
     */
    private static boolean typedSupports(AiModel model, AiModelTypeEnum requiredType,
                                         AiModelCapabilityEnum requiredInput,
                                         AiModelCapabilityEnum requiredOutput) {
        if (model == null || !requiredType.getCode().equals(normalizeType(model.getModelType()))) {
            return false;
        }
        return capabilities(model.getInputCapabilities()).contains(requiredInput)
            && capabilities(model.getOutputCapabilities()).contains(requiredOutput);
    }

    /**
     * 解析能力串（null/空白/非法 → 空集合，不抛异常）
     */
    private static EnumSet<AiModelCapabilityEnum> capabilities(String csv) {
        try {
            return AiModelCapabilityEnum.parse(csv);
        } catch (IllegalArgumentException e) {
            // 存量脏数据（未知能力码）按能力不足处理，由调用方降级或提示修正配置
            return EnumSet.noneOf(AiModelCapabilityEnum.class);
        }
    }

    private static String normalizeType(String modelType) {
        return modelType == null ? "" : modelType.trim().toUpperCase(Locale.ROOT);
    }
}
