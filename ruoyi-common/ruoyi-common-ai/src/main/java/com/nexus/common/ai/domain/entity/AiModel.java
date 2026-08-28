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

    /** 上下文窗口大小（Token 数） */
    private Integer contextWindow;

    /** 输入能力（逗号分隔，字典 ai_model_capability，如 TEXT,IMAGE） */
    private String inputCapabilities;

    /** 输出能力（逗号分隔，字典 ai_model_capability，如 TEXT,VECTOR） */
    private String outputCapabilities;

    /** API 协议（字典 ai_model_api_protocol，NULL 表示由厂商类型推断） */
    private String apiProtocol;

    /** 向量维度（仅 EMBEDDING 模型使用） */
    private Integer embeddingDimension;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 是否默认模型（Y是 N否，字典 sys_yes_no） */
    private String isDefault;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
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
