package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Prompt 模板业务对象
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiPromptTemplate.class, reverseConvertGenerate = false)
public class AiPromptTemplateBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /** 模板标识 */
    @NotBlank(message = "模板标识不能为空")
    private String templateKey;

    /** 模板内容 */
    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 描述 */
    private String description;

    /** 是否默认模板 */
    private String isDefault;

    /** 版本号 */
    private Integer version;

    /** 状态 */
    private String status;

    /** 主键数组（批量删除用） */
    private Long[] ids;
}
