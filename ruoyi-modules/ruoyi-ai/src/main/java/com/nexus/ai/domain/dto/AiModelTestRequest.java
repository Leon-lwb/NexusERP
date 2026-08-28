package com.nexus.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 模型连接测试请求 DTO（草稿测试，无需先保存模型）
 * <p><b>安全约束：本对象绝不包含 API Key 字段</b>——密钥只从 ai_provider 行内读取
 * （Jasypt 解密后仅在内存中使用），请求体只携带厂商 ID 与模型草稿参数。
 * 校验注解与 {@code AiModelBo} 保持一致。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Data
public class AiModelTestRequest {

    /** 关联厂商ID（连接测试用该厂商的 apiHost/apiKey） */
    @NotNull(message = "厂商ID不能为空")
    private Long providerId;

    /** 模型名称（运行时实际调用的 model id，如 deepseek-chat） */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /** 显示名称（可选，仅日志排查用） */
    private String displayName;

    /** 类型: TEXT/VISION/AUDIO/EMBEDDING/RERANK/IMAGE(字典 ai_model_type) */
    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    /** 输入能力（逗号分隔，字典 ai_model_capability，如 TEXT,IMAGE） */
    private String inputCapabilities;

    /** 输出能力（逗号分隔，字典 ai_model_capability，如 TEXT,VECTOR） */
    private String outputCapabilities;

    /** API 协议（字典 ai_model_api_protocol，NULL 表示由厂商类型推断默认协议） */
    private String apiProtocol;

    /** 上下文窗口大小（Token 数） */
    @Positive(message = "上下文窗口必须为正整数")
    private Integer contextWindow;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 向量维度（仅 EMBEDDING 模型使用，用于与实测维度比对） */
    @Positive(message = "向量维度必须为正整数")
    private Integer embeddingDimension;

    /**
     * 图片生成测试二次确认（默认 false）。
     * <p>文生图连接测试会产生实际计费，必须显式置 true 才执行。</p>
     */
    private boolean confirmBillableImageTest = false;
}
