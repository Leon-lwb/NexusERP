package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 是否默认模型（1是 0否） */
    private String isDefault;

    /** 状态（1正常 0停用） */
    private String status;

    /** 批量删除ID集合 */
    private Long[] ids;
}
