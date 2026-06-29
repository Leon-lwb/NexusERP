package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识图谱实例实体
 * <p>对应数据库表 ai_knowledge_graph，每个知识库对应一个图谱实例，记录构建状态与统计</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_graph")
public class AiKnowledgeGraph extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 知识库ID */
    private Long knowledgeId;

    /** 图谱名称 */
    private String graphName;

    /** 状态（0未构建 1构建中 2已完成 3失败） */
    private String graphStatus;

    /** 实体节点数 */
    private Integer nodeCount;

    /** 关系数 */
    private Integer relationshipCount;

    /** 社区数 */
    private Integer communityCount;

    /** 抽取用的LLM模型ID */
    private Long modelId;

    /** 实体类型（逗号分隔） */
    private String entityTypes;

    /** 关系类型（逗号分隔） */
    private String relationTypes;

    /** 图谱配置（JSON） */
    private String config;

    /** 错误信息 */
    private String errorMessage;
}
