package com.nexus.common.ai.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;

/**
 * AI知识库文档片段业务对象
 * <p>用于片段的新增/修改操作参数接收</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiKnowledgeChunk.class, reverseConvertGenerate = false)
public class AiKnowledgeChunkBo extends BaseEntity {

    /** 主键 */
    private Long id;

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
}
