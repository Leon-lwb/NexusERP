package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识图谱实体节点
 * <p>对应数据库表 ai_kg_entity，存储从知识库切片中抽取的实体及其合并信息</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_kg_entity")
public class AiKgEntity extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 图谱ID */
    private Long graphId;

    /** 实体名称 */
    private String entityName;

    /** 实体类型 */
    private String entityType;

    /** 实体描述 */
    private String description;

    /** 置信度（0-1） */
    private Double confidence;

    /** 来源切片ID */
    private Long sourceChunkId;

    /** 被提及次数（同名合并） */
    private Integer mentionCount;

    /** 所属社区ID */
    private Long communityId;
}
