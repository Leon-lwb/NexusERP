package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 知识图谱实例业务对象
 * <p>用于图谱构建参数接收与列表查询</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKnowledgeGraph.class, reverseConvertGenerate = false)
public class AiKnowledgeGraphBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 知识库ID */
    private Long knowledgeId;

    /** 抽取用的LLM模型ID */
    private Long modelId;

    /** 图谱名称 */
    private String graphName;

    /** 状态（0未构建 1构建中 2已完成 3失败） */
    private String graphStatus;

    /** 实体类型（逗号分隔） */
    private String entityTypes;

    /** 关系类型（逗号分隔） */
    private String relationTypes;
}
