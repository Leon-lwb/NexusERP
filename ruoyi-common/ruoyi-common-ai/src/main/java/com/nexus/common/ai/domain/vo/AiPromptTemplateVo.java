package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI Prompt 模板视图对象
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiPromptTemplate.class)
public class AiPromptTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "模板ID")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 模板名称 */
    @ExcelProperty(value = "模板名称")
    private String templateName;

    /** 模板标识 */
    @ExcelProperty(value = "模板标识")
    private String templateKey;

    /** 模板内容 */
    @ExcelProperty(value = "模板内容")
    private String content;

    /** 描述 */
    @ExcelProperty(value = "描述")
    private String description;

    /** 是否默认模板 */
    @ExcelProperty(value = "是否默认")
    private String isDefault;

    /** 版本号 */
    @ExcelProperty(value = "版本号")
    private Integer version;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;
}
