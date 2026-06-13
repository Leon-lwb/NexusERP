package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiSqlExample;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI SQL Few-Shot 示例业务对象
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiSqlExample.class, reverseConvertGenerate = false)
public class AiSqlExampleBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 用户问题（自然语言） */
    @NotBlank(message = "用户问题不能为空")
    private String question;

    /** SQL 模板 */
    @NotBlank(message = "SQL模板不能为空")
    private String sqlTemplate;

    /** 业务分类 */
    @NotBlank(message = "业务分类不能为空")
    private String category;

    /** 描述 */
    private String description;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（1正常 0停用） */
    private String status;
}
