package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 模型配置业务对象 ai_model
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiModel.class, reverseConvertGenerate = false)
public class AiModelBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 关联厂商ID */
    @NotNull(message = "厂商ID不能为空")
    private Long providerId;

    /** 模型名称（如 deepseek-chat） */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /** 显示名称 */
    private String displayName;

    /** 类型: CHAT/EMBEDDING/RERANK */
    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    /** 上下文窗口大小（Token 数） */
    @Positive(message = "上下文窗口必须为正整数")
    private Integer contextWindow;

    /** 输入能力（逗号分隔，字典 ai_model_capability，如 TEXT,IMAGE） */
    private String inputCapabilities;

    /** 输出能力（逗号分隔，字典 ai_model_capability，如 TEXT,VECTOR） */
    private String outputCapabilities;

    /** API 协议（字典 ai_model_api_protocol，NULL 表示由厂商类型推断） */
    private String apiProtocol;

    /** 向量维度（仅 EMBEDDING 模型使用） */
    @Positive(message = "向量维度必须为正整数")
    private Integer embeddingDimension;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 是否默认模型（Y是 N否，字典 sys_yes_no） */
    private String isDefault;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;

    /** 查询条件：要求输入能力包含该值（逗号分隔列成员判断，字典 ai_model_capability，如 TEXT） */
    private String requiredInputCapability;

    /** 查询条件：要求输出能力包含该值（逗号分隔列成员判断，字典 ai_model_capability，如 VECTOR） */
    private String requiredOutputCapability;

    /** 输入价格（元/百万Token） */
    private BigDecimal inputPricePerMillion;

    /** 输出价格（元/百万Token） */
    private BigDecimal outputPricePerMillion;

    /** 币种：CNY/USD */
    private String currency;

    /** 批量删除ID集合 */
    private Long[] ids;
}
