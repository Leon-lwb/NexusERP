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

    /** 租户编号 */
    private Long tenantId;

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

    /** 最大输出 Token */
    @ExcelProperty(value = "最大Token数")
    private Integer maxTokens;

    /** 温度参数 */
    @ExcelProperty(value = "温度参数")
    private BigDecimal temperature;

    /** 是否默认模型（1是 0否） */
    @ExcelProperty(value = "是否默认")
    private String isDefault;

    /** 状态（1正常 0停用） */
    @ExcelProperty(value = "状态")
    private String status;

    /** 厂商 API 地址（来自 ai_provider 关联查询） */
    private String apiHost;

    /** 厂商类型（来自 ai_provider 关联查询） */
    @ExcelProperty(value = "厂商类型")
    private String providerType;
}
