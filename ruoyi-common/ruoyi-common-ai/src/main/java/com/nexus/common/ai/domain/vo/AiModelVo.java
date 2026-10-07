package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiModel;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 模型配置视图对象 ai_model
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiModel.class)
public class AiModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "模型ID")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 关联厂商ID */
    @ExcelProperty(value = "厂商ID")
    private Long providerId;

    /** 厂商名称（来自 ai_provider 关联查询） */
    @ExcelProperty(value = "厂商名称")
    private String providerName;

    /** 模型名称（如 deepseek-chat） */
    @ExcelProperty(value = "模型名称")
    private String modelName;

    /** 显示名称 */
    @ExcelProperty(value = "显示名称")
    private String displayName;

    /** 类型: CHAT/EMBEDDING/RERANK */
    @ExcelProperty(value = "模型类型")
    private String modelType;

    /** 上下文窗口大小（Token 数） */
    @ExcelProperty(value = "上下文窗口")
    private Integer contextWindow;

    /** 输入能力（逗号分隔，字典 ai_model_capability，如 TEXT,IMAGE） */
    @ExcelProperty(value = "输入能力")
    private String inputCapabilities;

    /** 输出能力（逗号分隔，字典 ai_model_capability，如 TEXT,VECTOR） */
    @ExcelProperty(value = "输出能力")
    private String outputCapabilities;

    /** API 协议（字典 ai_model_api_protocol，NULL 表示由厂商类型推断） */
    @ExcelProperty(value = "API协议")
    private String apiProtocol;

    /** 向量维度（仅 EMBEDDING 模型使用） */
    @ExcelProperty(value = "向量维度")
    private Integer embeddingDimension;

    /** 最大输出 Token */
    @ExcelProperty(value = "最大输出Token数")
    private Integer maxTokens;

    /** 温度参数 */
    @ExcelProperty(value = "温度参数")
    private BigDecimal temperature;

    /** 是否默认模型（Y是 N否，字典 sys_yes_no） */
    @ExcelProperty(value = "是否默认")
    private String isDefault;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    @ExcelProperty(value = "状态")
    private String status;

    /** 输入价格（元/百万Token） */
    @ExcelProperty(value = "输入价格")
    private BigDecimal inputPricePerMillion;

    /** 输出价格（元/百万Token） */
    @ExcelProperty(value = "输出价格")
    private BigDecimal outputPricePerMillion;

    /** 币种：CNY/USD */
    @ExcelProperty(value = "币种")
    private String currency;

    /** 厂商 API 地址（来自 ai_provider 关联查询） */
    private String apiHost;

    /** 厂商类型（来自 ai_provider 关联查询） */
    @ExcelProperty(value = "厂商类型")
    private String providerType;
}
