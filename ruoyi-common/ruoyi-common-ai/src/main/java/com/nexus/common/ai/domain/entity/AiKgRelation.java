package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识图谱关系
 * <p>对应数据库表 ai_kg_relation，存储实体间的关系（有向边）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_kg_relation")
public class AiKgRelation extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
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
    private String relationType;

    /** 关系描述 */
    private String description;

    /** 关系强度（0-10） */
    private Integer strength;

    /** 置信度（0-1） */
    private Double confidence;

    /** 来源切片ID */
    private Long sourceChunkId;
}
