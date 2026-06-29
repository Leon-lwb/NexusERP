package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 知识图谱关系视图对象 ai_kg_relation
 * <p>sourceEntityName/targetEntityName 为联表展示字段，由 Service 回填</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKgRelation.class)
public class AiKgRelationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 图谱ID */
    private Long graphId;

    /** 头实体ID */
    private Long sourceEntityId;

    /** 尾实体ID */
    private Long targetEntityId;

    /** 关系类型 */
    @ExcelProperty(value = "关系类型")
    private String relationType;

    /** 关系描述 */
    @ExcelProperty(value = "关系描述")
    private String description;

    /** 关系强度（0-10） */
    @ExcelProperty(value = "强度")
    private Integer strength;

    /** 置信度（0-1） */
    @ExcelProperty(value = "置信度")
    private Double confidence;

    /** 来源切片ID */
    private Long sourceChunkId;

    /** 头实体名称（联表回填） */
    @ExcelProperty(value = "头实体")
    private String sourceEntityName;

    /** 尾实体名称（联表回填） */
    @ExcelProperty(value = "尾实体")
    private String targetEntityName;
}
