package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.common.ai.domain.entity.AiProvider;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 厂商配置视图对象 ai_provider
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiProvider.class)
public class AiProviderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @ExcelProperty(value = "主键")
    private Long id;

    /**
     * 租户编号
     */
    private Long tenantId;

    /**
     * 厂商名称
     */
    @ExcelProperty(value = "厂商名称")
    private String name;

    /**
     * API 地址
     */
    @ExcelProperty(value = "API地址")
    private String apiHost;

    /**
     * API Key（脱敏显示）
     */
    @ExcelProperty(value = "API Key")
    private String apiKey;

    /**
     * 厂商类型: OPENAI/DASHSCOPE/ZHIPU/OLLAMA
     */
    @ExcelProperty(value = "厂商类型")
    private String providerType;

    /**
     * 状态（1正常 0停用）
     */
    @ExcelProperty(value = "状态")
    private String status;

}
