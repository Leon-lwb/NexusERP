package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 模型配置实体
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model")
public class AiModel extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 关联厂商ID */
    private Long providerId;

    /** 模型名称（如 deepseek-chat） */
    private String modelName;

    /** 显示名称 */
    private String displayName;

    /** 类型: CHAT/EMBEDDING/RERANK */
    private String modelType;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 是否默认模型（1是 0否） */
    private String isDefault;

    /** 状态（1正常 0停用） */
    private String status;

    /** 输入价格（元/百万Token） */
    private BigDecimal inputPricePerMillion;

    /** 输出价格（元/百万Token） */
    private BigDecimal outputPricePerMillion;

    /** 币种：CNY/USD */
    private String currency;

    /** 厂商 API 地址（来自 ai_provider 关联查询） */
    @TableField(exist = false)
    private String apiHost;

    /** 厂商 API Key（来自 ai_provider 关联查询） */
    @TableField(exist = false)
    private String apiKey;

    /** 厂商类型（来自 ai_provider 关联查询） */
    @TableField(exist = false)
    private String providerType;
}
