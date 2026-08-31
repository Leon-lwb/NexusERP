package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识库文档片段实体
 * <p>对应数据库表 ai_knowledge_chunk，存储文档分段后的文本片段及其向量ID</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_chunk")
public class AiKnowledgeChunk extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 文档ID */
    private Long documentId;

    /** 知识库ID */
    private Long knowledgeId;

    /** 片段内容 */
    private String content;

    /** 片段序号 */
    private Integer chunkIndex;

    /** 向量数据库中的ID */
    private String vectorId;

    /** 图谱抽取状态（0待处理 1处理中 2已完成 3失败） */
    private String extractionStatus;

    /** 抽取实体数 */
    private Integer entityCount;

    /** 抽取关系数 */
    private Integer relationCount;

    /** 抽取消耗token */
    private Long tokenUsed;

    /** 抽取错误信息 */
    private String kgErrorMessage;
}
