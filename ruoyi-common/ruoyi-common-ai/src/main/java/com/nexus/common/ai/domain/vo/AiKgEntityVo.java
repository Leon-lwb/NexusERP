package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 知识图谱实体视图对象 ai_kg_entity
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKgEntity.class)
public class AiKgEntityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号 */
    private String tenantId;

    /** 图谱ID */
    private Long graphId;

    /** 实体名称 */
    @ExcelProperty(value = "实体名称")
    private String entityName;

    /** 实体类型 */
    @ExcelProperty(value = "实体类型")
    private String entityType;

    /** 实体描述 */
    @ExcelProperty(value = "描述")
    private String description;

    /** 置信度（0-1） */
    @ExcelProperty(value = "置信度")
    private Double confidence;

    /** 来源切片ID */
    private Long sourceChunkId;

    /** 被提及次数 */
    @ExcelProperty(value = "提及次数")
    private Integer mentionCount;

    /** 所属社区ID */
    private Long communityId;
}
